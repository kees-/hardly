I recently recreated parts of a small static website at [hardly.link](https://hardly.link). In an effort to avoid hardcoding content and make the site extensible as a portfolio or blog, these are a few command line utilities to build and deploy the site from markdown and data files. (Intended locally, there is no automation.)

The tools used are

- [babashka](https://book.babashka.org/)
- [selmer](https://github.com/yogthos/Selmer)
- [markdown-clj](https://github.com/yogthos/markdown-clj)

Everything is powerful enough where I just need the basic commands from each tool/lib, there is no monkey business involved in throwing a simple site up.

```
$ bb tasks

The following tasks are available:

clean     Removes target.
resources Copy all generic assets to `target`.
index     Writes an index file with all given HTML content.
build     Full build of site.
changes   Show what's changed between local and deployed site.
deploy    Builds and publishes the site online.
```

## Caveats

- Metadata provided at the start of markdown files doesn't reformat itself, so formatting must be as an HTML string, and only content on the same line as the key is respected.
- Needs babashka and `awscli` installed.
- Provide `{:bucket name :dist dist-id}` in `resources/credentials.edn` to sync and invalidate.
- HTML formatting within the content subtemplates is valid, but not very pretty.
