# Portal API References

## DBLP

- API Doc: http://dblp.uni-trier.de/faq/How+to+use+the+dblp+search+API.html

Examples:

- http://dblp.org/search/publ/api?format=json&h=1000&q=Herbert%20Van%20de%20Sompel

## Slideshare

- API Doc: https://www.slideshare.net/developers/documentation

Examples:

- https://www.slideshare.net/api/2/get_slideshows_by_user?api_key=61KWSBpM&ts=1512494760&hash=8972e115198f362b41befca779dcaff80e48e589&username_for=hvdsomp

_Note_: The hash will have to be generated dynamically

## Github

- API Doc: https://developer.github.com/v3/activity/events/

Examples:

- https://api.github.com/users/hariharshankar/events

## Twitter

- API Doc: https://developer.twitter.com/en/docs/tweets/timelines/api-reference/get-statuses-user_timeline.html

## MS Academic

- API Playground: https://westus.dev.cognitive.microsoft.com/docs/services/56332331778daf02acc0a50b/operations/565d753be597ed16ac3ffc03/console
- API Doc: https://docs.microsoft.com/en-us/azure/cognitive-services/academic-knowledge/paperentityattributes

## CrossRef

- API Doc: https://www.eventdata.crossref.org/guide/service/query-api/

Examples:

- Events for a DOI: https://query.eventdata.crossref.org/events?filter=obj-id:%22https://doi.org/10.1145/1462198.1462199%22

## Hypothesis

- API Doc: http://h.readthedocs.io/en/latest/api-reference/

## Stackoverflow

- API Doc: https://api.stackexchange.com/docs

## Wikipedia

- API Doc: https://www.mediawiki.org/wiki/API:Main_page
- User contribution query parameters: https://en.wikipedia.org/w/api.php?action=help&modules=query%2Busercontribs

## Medium

- Rest API Docs: https://github.com/Medium/medium-api-docs
- RSS Feed: https://medium.com/feed/@{user_name}
- Recommended (claps) RSS Feed: https://medium.com/feed/@{user_name}/has-recommended

## Figshare

- API Docs: https://docs.figshare.com/

## Publons

- Publons API Docs: https://publons.com/api/v2/
- Request auth token: POST https://publons.com/api/v2/token/ -d 'username={}&password={}'
- Get academic profile and reviews: https://publons.com/api/v2/academic/{ORCID}/
- Get all pre peer-reviews for publications: "https://publons.com/api/v2/academic/review/?academic={user_id}&pre=true"
- Get all post peer-reviews for publications: "https://publons.com/api/v2/academic/review/?academic={user_id}&pre=false"

_Note_: Max 100 requests per day

## Blogger

- Requires OAuth:
- API Docs: https://developers.google.com/blogger/docs/3.0/getting_started
- Get blog id via blog URL: https://www.googleapis.com/blogger/v3/blogs/byurl?url={BlogURL}&key={YOUR_API_KEY}
- Get posts from blog: https://www.googleapis.com/blogger/v3/blogs/{blogID}/posts?maxResults=100&fields=etag%2Citems(author%2Cblog%2CcustomMetaData%2Cetag%2Cid%2Cimages%2Ckind%2Cpublished%2Cstatus%2Ctitle%2CtitleLink%2Cupdated%2Curl)%2Ckind%2CnextPageToken&key={YOUR_API_KEY}
- Paginate using `nextPageToken` argument provided from previous response

_Note_: 100 items per request seems to not return a 500 server error
