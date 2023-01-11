(ns scripts.markdown
  (:require [clojure.string :as s]
            [markdown.common]
            [markdown.transformers]
            [scripts.elements :as elements]))

(defn includer
  "call elements/render on lines of markdown formatted as
   `%include content-type file-path`."
  [text state]
  (if (s/starts-with? text "%include")
    (let [[_ content-type file] (s/split text #"\s+")]
      [(elements/render [content-type {:file file}])
       (assoc state :paragraph true :includer true)])
    [text state]))

(defn inline-code-lang
  "Workaround inline code blocks for prism.js to properly highlight."
  [text state]
  [(s/replace text #"<code>" "<code class=\"lang-none\">") state])

(def transformers
  "Pre- and post-processors for custom markdown."
  (reduce into [[includer]
                markdown.transformers/transformer-vector
                [inline-code-lang]]))

;; ========== SKETCHY REDEFS ===================================================
(defn close-paragraph
  "Gymnastics to neither escape nor wrap `includer` blocks in `<p>` tags."
  [text {:keys [next-line paragraph includer] :as state}]
  (cond
    includer [text (dissoc state :paragraph)]

    (and paragraph (some-> next-line s/trim (s/ends-with? "```")))
    [(str text "</p>") (dissoc state :paragraph)]

    :else [text state]))

(defn footer
  "Identical to `markdown.common/footer` with `target='_self'` added to links."
  [footnotes]
  (if (empty? (:processed footnotes))
    ""
    (->> (:processed footnotes)
         (into (sorted-map))
         (reduce
          (fn [footnotes [id label]]
            (str footnotes
                 "<li id='fn-" id "'>"
                 (apply str (interpose " " label))
                 "<a href='#fnref" id "' target='_self'>&#8617;</a></li>"))
          "")
         (format "<ol class='footnotes'>%s</ol>"))))
