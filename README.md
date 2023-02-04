I recently recreated parts of a small static website at [hardly.link](https://hardly.link). In an effort to avoid hardcoding content and make the site extensible as a portfolio or blog, these are a few command line utilities to build and deploy the site from markdown and data files. (Intended locally, there is no automation.)

The tools used are

- [babashka](https://book.babashka.org/)
- [selmer](https://github.com/yogthos/Selmer)
- [markdown-clj](https://github.com/yogthos/markdown-clj)

Everything is powerful enough where I just need the basic commands from each tool/lib. There is no monkey business involved in throwing a simple site up.

```
$ bb tasks

The following tasks are available:

clean        Removes target.
resources    Copy all generic assets to `target`.
index        Writes an index file with all given HTML content.
build        Full build of site.
changes:soft Show what's changed without rebuild.
changes      Show what's changed between local and deployed site.
deploy:soft  Publish current files without rebuild.
deploy       Builds and publishes the site online.
```

The project is inspired by [quickblog](https://github.com/borkdude/quickblog)! Note! I don't have hot reload. For actually iterating content, I'm using a Markdown editor. I built this to learn and incorporate extensible templates. For your own simple blog, I recommend quickblog instead.

## Structure

What does this do?

### Writing HTML

`scripts.render/write!` takes a map with the keys `:data`, `:template`, and `:outfile`.

`:template` specifies an existing selmer-ready HTML file. The resulting HTML will be written to `:outfile`. See the babashka task `index` for an example.

The behavior varies depending on what data is specified:

### ...from EDN

```
:data "path/to/file.edn"
```

Currently, a `:title-block` and main `:content` is given in EDNs. (I should generalize/extend.)

Content can be defined in blocks, either:

- plain HTML strings `{:text "<em>Hi</em>"}`,
- paths to additional files `{:file "something.md"}`,
- or data structures particular to the content intended to render.

```clojure
{:title-block {:text "My page"}
 :content [[:interstice {:text "Welcome!"}
           [:interstice {:file "content/subhead.md"}
           [:list {:items [{:date 2022
                            :link "some/project"
                            :name "this"
                            :desc "This is a new project"}
                           {:date 2023
                            :name "other"}]}]
           [:quotation {:file "content/quotes/from-book.md"}]
           [:quotation {:content "Favorite quote from a book."
                        :title "Passage"
                        :attribution "Author"}]]]]]}
```

Yes, confusing, but pretty flexible!

It's doesn't take much to create a good amount of text- and image-based content.

### ...from Markdown

```
:data "path/to/file.md"
```

Metadata is read from the head of the file, and the rest of the body is treated as the main content.

Markdown files will **recursively render linked files into inline HTML** by including the following syntax:

```md
%include content-type file-path
```

E.g.,

```md
Generic paragraph content

%include quotation content/book-quote.md

## Next section

More text
```

Will render as:

```html
<p>Generic paragraph content</p>
<div class="quotation">
  <details>
    ... etc
  </details>
</div>
<h2>Next section</h2>
<p>More text</p>
```

I use some pretty aggressive workarounds (`markdown-clj` core redefs) to format well without trailing tags, so apologies if it breaks elsewhere.

### Chronological posts (blogging...)

```scripts.render/write-all-posts!```, when given a path to a `:source-dir` and the individual name of the `:posts-dir`, will `write!` pages for all the files in the directory, mirrored in the target, and an index file with a chronological list of all of them.

The locations are kept generic for multiple streams, website sections etc. In my instance, **I write posts directly in Obsidian, and a symlinked folder renders everything in this project!** It's actually a great workflow.

```write-all-posts!``` will ignore Markdown files starting with `-`, much like [borkdude/quickblog](https://github.com/borkdude/quickblog).

## Types of content

Either in EDN content blocks or Markdown %includes, I specify various subtemplates for repeated types of content.

So far I've used:

Key | Result
--- | ---
`interstice` | Generic text directly rendered as HTML.
`list` | List of links with a date, short name, long name, and description. Intended for portfolio outlinks.
`quotation` | Collapsible details section with a block of text, a title, and source attribution.

### Extending blocks

1. Create a template file containing an HTML fragment and all the necessary selmer tags. **The content-type is the filename prefix.**
2. If needed, define a new method for `scripts.elements/render` with the new content-type. The default method covers simple cases.
3. Provide any styles in the CSS resource files.
3. As stated above, reference the new content-type with `[:thing ...]` in EDN or `%include thing file` in Markdown.

## Project caveats

- Needs Babashka and `awscli` installed.
- Provide `{:bucket name :dist dist-id}` in `resources/credentials.edn` to sync and invalidate.
- Metadata provided at the start of markdown files doesn't reformat itself, so formatting must be as an HTML string, and only content on the same line as the key is respected.
- The tasks wrap `aws s3 sync` CLI commands, `--delete` is not included as a flag, so removed/renamed posts will be orphaned in the bucket. This is just the case because I host some other things on the site that I don't want scrubbed.
- HTML formatting within the content subtemplates is valid, but not very pretty.
