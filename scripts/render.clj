(ns scripts.render
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as s]
            [markdown.core :as md]
            [scripts.elements :as elements]
            [selmer.parser :as p]))

(defn limit
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

(defn slugify
  ([s] (-> s s/lower-case (s/replace #"[_\W]" "-")))
  ([s & limits] (slugify (apply limit s limits))))

(defn hydrate
  "Renders HTML string for the index file based on local paths to source data and template."
  [source template]
  (let [data (-> (slurp source)
                 edn/read-string
                 (update :content #(reduce str (mapv elements/render %)))
                 (update :title-block elements/file-or-content->md))]
    (p/render-file template data)))

(defn write-index!
  [{:keys [content template target]}]
  (let [html (hydrate content template)]
    (spit target html)))

(defmulti pre-render fs/extension)

(defmethod pre-render "md"
  [path]
  (let [{:keys [metadata html]} (md/md-to-html-string-with-meta
                                 (slurp (fs/file path))
                                 :heading-anchors true
                                 :footnotes? true
                                 :code-style #(str "class=\"lang-" % "\""))
        title (-> path fs/file-name fs/split-ext first)
        post (merge {:title title
                     :slug (slugify title)
                     :content html}
                    metadata)]
    post))

(defn write!
  [{:keys [data outfile template]}]
  (spit outfile (p/render-file template data)))

(defn write-all-posts!
  [{:keys [dir]}]
  (let [paths (fs/list-dir dir)]
    (doseq [post (map pre-render paths)]
      (write! {:data post
               :template "templates/post.html"
               :outfile (format "target/public/posts/%s.html" (:slug post))}))))
