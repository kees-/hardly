(ns scripts.elements
  (:require [markdown.core :as md]
            [selmer.parser :as p]))

(defn file-or-text->md
  "Parse markdown string from :text, or read from :file if present."
  [m]
  (md/md-to-html-string (if-let [f (:file m)]
                          (slurp f)
                          (:text m))))

(defmulti render
  "Coerce different types of content to HTML. For each content type,
   provide a template in `templates/` and define new methods as necessary."
  #(keyword (first %)))

(defmethod render :quotation
  [[_ data]]
  (let [from-file (when-let [f (:file data)]
                    (md/md-to-html-string-with-meta (slurp f)))
        content (merge (:metadata from-file)
                       (select-keys data [:title :attribution])
                       {:content (or (:html from-file)
                                     (:text data))})]
    (p/render-file "templates/quotation.html" content)))

(defmethod render :default
  [[template m]]
  (let [file (format "templates/%s.html" (name template))]
    (p/render-file file (assoc m :content (file-or-text->md m)))))
