```html
<a
href="https://wikipedia.org/wiki/Memento_Project?oldid=662035096" target="_blank"
data-originalurl="https://wikipedia.org/wiki/Memento_Project"
data-versiondate="2015-05-12T17:59:53Z"
data-versionurl="https://myresearch.institute/mementos/20150512202631/https://wikipedia.org/wiki/Memento_Project?oldid=662035096"
>https://wikipedia.org/wiki/Memento_Project?oldid=662035096</a>
```

## Robust links rules

- memento created always goes into data-versionurl
- OriginalResource always goes to data-originalurl
- mementoDatetime always goes into data-versiondate
- Was the originally tracked resource a memento?
    - Yes: the objects href from the tracker message goes into the href
    - No: OriginalResource goes in href

## Choosing what goes in href

UI Algorithm for generating robustlinks:

```
First look at archiver prov:wasInformedBy and get id with type tracker:Tracker
    Get tracker with id
    for item in tracker.object.items:
        for res in archiver.result.items:
            if item has OriginalResource:
                robustlink.href = tracker.object.item.href
            else:
                robustlink.href = archiver.result.item.OriginalResource
```
