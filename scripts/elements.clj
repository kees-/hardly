(ns scripts.elements
  (:require [markdown.core :as md]
            [selmer.parser :as p]))

(defn file-or-content->md
  "Parse markdown string from :content, or read from :file if present."
  [m]
  (md/md-to-html-string (if-let [f (:file m)]
                          (slurp f)
                          (:content m))))

(defmulti render
  "Extensible mm to coerce different types of content to HTML.
   For each content type, provide a template in `templates/`
   and define a new method as necessary."
  first)

(defmethod render :quotation
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

(defmethod render :default
  [[template m]]
  (let [file (format "templates/%s.html" (name template))]
    (p/render-file file (assoc m :content (file-or-content->md m)))))
