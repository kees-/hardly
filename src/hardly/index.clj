(ns hardly.index
  (:require [clojure.edn :as edn]
            [markdown.core :as md]
            [selmer.parser :as p]))

(defn- file-or-content->md
  "Parse markdown string from :content, or read from :file if present."
  [m]
  (md/md-to-html-string (if-let [f (:file m)]
                          (slurp f)
                          (:content m))))

(defmulti ^:private item
  "Extensible mm to coerce different types of content to HTML.
   For each content type, provide a template in `templates/`
   and define a new method as necessary."
  first)

(defmethod item :quotation
  [[_ content]]
  (let [from-file (when-let [f (:file content)]
                    (-> (slurp f)
                        md/md-to-html-string-with-meta
                        (update :metadata #(update-vals % first))))
        content (merge (:metadata from-file)
                       (select-keys content [:title :attribution])
                       {:content (or (:html from-file)
                                     (:content content))})]
    (p/render-file "templates/quotation.html" content)))

(defmethod item :default
  [[template m]]
  (let [file (format "templates/%s.html" (name template))]
    (p/render-file file (assoc m :content (file-or-content->md m)))))

(defn- hydrate
  "Renders HTML string for the index file based on local paths to source data and template."
  [source template]
  (let [data (-> (slurp source)
                 edn/read-string
                 (update :content #(reduce str (mapv item %)))
                 (update :title-block file-or-content->md))]
    (p/render-file template data)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn write!
  []
  (let [html-str (hydrate "resources/content/index.edn" "templates/index.html")]
    (spit "target/public/index.html" html-str)))
