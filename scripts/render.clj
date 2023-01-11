(ns scripts.render
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as s]
            [markdown.core :as md]
            [markdown.common]
            [markdown.transformers]
            [scripts.elements :as elements]
            [scripts.markdown :as mark]
            [selmer.parser :as p]))

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
          title (-> path fs/file-name fs/split-ext first)
          post (merge {:title title
                       :slug (slugify title)
                       :content (:html parsed)}
                      (:metadata parsed))]
      post)))

(defn write!
  "Write a template given in and outfile paths.
   `data` can be a filepath or a clj object."
  [{:keys [data outfile template]}]
  (spit outfile (p/render-file template (cond-> data
                                          (string? data) pre-render))))

(defn write-all-posts!
  "Writes HTML pages for all posts, and an index linking them."
  [{:keys [source-dir posts-dir]}]
  (let [paths (remove #(= \- (first (fs/file-name %)))
                      (fs/list-dir (format "%s/%s" source-dir posts-dir)))
        posts (map #(assoc (pre-render %) :posts-dir posts-dir)
                   paths)]
    (doseq [post posts]
      (write! {:data post
               :template "templates/post.html"
               :outfile (format "target/public/%s/%s.html" posts-dir (:slug post))}))
    (write! {:data {:posts (reverse (sort-by :date posts))}
             :template "templates/posts-index.html"
             :outfile (format "target/public/%s/index.html" posts-dir)})))
