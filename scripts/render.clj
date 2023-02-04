(ns scripts.render
  (:require [babashka.fs :as fs]
            [clojure.data.xml :as xml]
            [clojure.edn :as edn]
            [clojure.string :as s]
            [markdown.core :as md]
            [markdown.common]
            [markdown.transformers]
            [scripts.elements :as elements]
            [scripts.markdown :as mark]
            [selmer.parser :as p])
  (:import [java.time.format DateTimeFormatter]))

(defn- limit
  "Smart limiting for trimming strings with a maximum length.
   Limited strings will be shrunk through the first `break` regex,
   defaulting to whitespace. An ellipsis for limited strings can be provided."
  ([s len] (limit s len #"\s" ""))
  ([s len break] (limit s len break ""))
  ([s len break ellipsis]
   (if (<= (count s) len)
     s
     (let [tr (subs s 0 (- len (count ellipsis) -1))]
       (str
        (if (re-find break tr)
          (s/replace-first tr (re-pattern (str break "[^" break "]*?$")) "")
          tr)
        ellipsis)))))

(defn- slugify
  "Make a string friendly for URLs."
  ([s] (-> s s/lower-case (s/replace #"[_\W]" "-")))
  ([s & limits] (slugify (apply limit s limits))))

;; ========== XML ==============================================================
;; https://github.com/borkdude/quickblog/blob/main/src/quickblog/api.clj#L362-L424
(xml/alias-uri 'atom "http://www.w3.org/2005/Atom")

#_#_

(defn- timestamp [yyyy-MM-dd]
  (let [in-fmt (DateTimeFormatter/ofPattern "yyyy-MM-dd")
        local-date (java.time.LocalDate/parse yyyy-MM-dd in-fmt)]
    (.format (java.time.ZonedDateTime/of (.atTime local-date 00 00 00) java.time.ZoneOffset/UTC)
             (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ssxxx"))))

(defn- stamp
  [inst]
  (.format (java.time.LocalDate/parse inst (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ssxxx"))
           (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ssxxx")))

(defn- now
  []
  (let [fmt (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ssxxx")
        now (java.time.ZonedDateTime/now java.time.ZoneOffset/UTC)]
    (.format now fmt)))

(def ^:private timestamp
  (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssXXX"))

(defn- feed-item
  [post posts-dir base-url]
  (let [{:keys [title date content slug]} post
        url (format "%s%s/%s.html" base-url posts-dir slug)]
    [::atom/entry
     [::atom/id url]
     [::atom/link {:href url}]
     [::atom/title title]
     [::atom/updated (.format timestamp date)]
     [::atom/content {:type "html"}
      [:-cdata content]]]))

(defn- feed
  [posts posts-dir]
  (let [url "https://hardly.link/"
        xml [::atom/feed
             {:xmlns "http://www.w3.org/2005/Atom"}
             [::atom/title "Hardly"]
             [::atom/link {:href (format "%s%s/feed.xml" url posts-dir) :rel "self"}]
             [::atom/link {:href url}]
             [::atom/updated (now)]
             [::atom/id url]
             [::atom/author
              [::atom/name "Kees"]]
             (for [post (reverse (sort-by :date posts))]
               (feed-item post posts-dir url))]]
    (-> xml xml/sexp-as-element xml/indent-str)))

(defmulti ^:private pre-render
  "Coerces data from a file into a format friendly to markdown-clj."
  fs/extension)

(defmethod pre-render :default
  [& _]
  (throw (Exception. "Unsupported file type.")))

(defmethod pre-render "edn"
  [path]
  (let [data (-> (slurp (fs/file path))
                 edn/read-string
                 (update :content #(reduce str (mapv elements/render %)))
                 (update :title-block elements/file-or-text->md))]
    data))

(defmethod pre-render "md"
  [path]
  (with-redefs [markdown.transformers/close-paragraph mark/close-paragraph
                markdown.transformers/footer mark/footer]
    (let [parsed (md/md-to-html-string-with-meta
                  (slurp (fs/file path))
                  :heading-anchors true
                  :footnotes? true
                  :replacement-transformers mark/transformers
                  :code-style #(format "class=\"lang-%s\"" %))
          title (-> path fs/file-name fs/split-ext first)]
      (merge {:title title
              :slug (slugify title)
              :content (:html parsed)}
             (:metadata parsed)))))

(defn write!
  "Write a template given in and outfile paths.
   `data` can be a filepath or a clj object."
  [{:keys [data outfile template]}]
  (let [data (cond-> data
               (string? data) pre-render)]
    (spit outfile (p/render-file template data))))

(defn- write-feed!
  [posts dir]
  (let [out (format "target/public/%s/feed.xml" dir)]
    (println "Writing" out)
    (spit out (feed posts dir))))

(defn- write-index!
  [posts posts-dir]
  (let [data {:posts (reverse (sort-by :date posts))}
        out (format "target/public/%s/index.html" posts-dir)]
    (println "Writing" out)
    (write! {:data data
             :template "templates/posts-index.html"
             :outfile out})))

(defn write-all-posts!
  "Writes HTML pages for all posts, and an index linking them."
  [{:keys [source-dir posts-dir]}]
  (println "Writing files from and to" posts-dir)
  (let [paths (remove #(s/starts-with? (fs/file-name %) "-")
                      (fs/list-dir (fs/path source-dir posts-dir)))
        posts (map #(assoc (pre-render %) :posts-dir posts-dir)
                   paths)]
    (doseq [post posts]
      (write! {:data post
               :template "templates/post.html"
               :outfile (format "target/public/%s/%s.html" posts-dir (:slug post))}))
    (write-feed! posts posts-dir)
    (write-index! posts posts-dir)))
