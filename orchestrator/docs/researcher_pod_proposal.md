# Researcher Pod

We envision a personal, dedicated, portable, self-contained pod that a researcher can deploy in her personal domain.  The researcher pod consists of two components: a **Personal Web Observatory** and a **Personal Communication Platform**. The former will be investigated first, the latter may be added in a next phase. However, requirements for the latter should be taken into account when exploring the former.

![image alt text](/docs/images/image_0.png)

**Personal Web Observatory** - This component consists itself of two subcomponents: the **artifact tracker** and the **artifact event store**. The artifact tracker tracks the artifacts left by the researcher in various social networks and web platforms. Newly discovered artifacts are communicated as event metadata to the artifact event store. The artifact event store provides the ability to access and display metadata about these artifacts in a variety of ways, e.g. details about a specific artifact, a list of all artifacts of a certain type or from a certain portal, a temporal list of artifacts. Through a combination of algorithmic and manual approaches, artifacts for which metadata is added to the artifact event store are also communicated to an institutional process aimed at capturing artifacts for long-term preservation.

**Personal Communication Platform** - This component provides the ability for the researcher to generate artifacts from within the pod, publish them in the pod, and possibly syndicate the content to other web platforms. This enables the researcher to retain personal control over the artifacts. This component also enables the researcher to interact (e.g. review, annotate) with artifacts in other research pods, and to engage on certain social networks directly from the pod, in both cases storing the result of these interactions in the personal pod. This enables the emergence of social research networks based on pods for which contributed content resides in a personal pod at any time. Just like artifacts discovered via the artifact tracker, metadata about artifacts published in the pod are added to the artifact event store, and hence also communicated to an institutional process aimed at capturing artifacts for long-term preservation.

## Desired Features:

* A dedicated domain for a researcher.

* A dedicated pod for a researcher that can easily be installed on an (institutional) hosting platform.

* The pod must be easily portable. When changing institutions, the researcher takes her domain and pod with her.  The pod is installed on the new institution’s hosting platform and the domain resolves there.

* The pod must support essential standards identified below, and should be designed to easily add support for additional standards in the future.

## Desired Features for the Personal Web Observatory:

* Consists of two autonomous building blocks: the **artifact tracker** and the **artifact event store**.

* The **artifact tracker** is based on the notion of connectors, whereby a connector for a web portal tracks a researcher’s artifacts in the portal. Each event tracked by the connector is communicated to the personal data store via Linked Data Notifications with AS2 payload. It is expected that connectors will evolve over time. As such, a central platform with up-to-date connectors is envisioned, with pods syncing with that platform as a means to have the most recent connectors locally available. The artifact tracker securely stores and maintains login information that allows each connector to access the researcher’s account on a web platform. For example, securely storing a researcher’s Twitter or Blogger OAuth token.

    * Essential Standards:[ Linked Data Notification](https://www.w3.org/TR/ldn/),[ webmention](https://www.w3.org/TR/webmention/),[ ActivityStreams 2](https://www.w3.org/TR/activitystreams-core/).

* The **artifact event store** maintains a database of AS2 payloads and makes the content accessible in a variety of ways to both humans and machines. Incoming AS2 payloads may need to be augmented upon ingestion in order to meet access requirements. In principle, ingestion of metadata pertaining to a new artifact leads to communicating artifact information to the institutional archival process (e.g. via Linked Data Notifications, ResourceSync) but configuration options give the researcher a level of control over this.

    * Essential Standards:[ ActivityStreams 2](https://www.w3.org/TR/activitystreams-core/),[ Memento](http://mementoweb.org/about/), Linked Data Notifications / ResourceSync.

    * Optional Standards:[ Linked Data Platform](https://www.w3.org/TR/ldp/).

* Single user authentication to allow:

    * Adding/updating web identities and credentials.

    * Fine tuning archival preferences.

    * Editing/Adding style sheets for display of artifact metadata views.

* Database backed storage for fast indexing and querying.

* Tools for analyzing the artifact information in the pod and reporting the results in various serializations for easy human and machine consumption. (HTML, JSON, RDF, etc)

    * Includes reporting artifacts that need to be archived to the Institutional Archival Process, as a result of automatic and manual selections.

## Desired Features for the Personal Communication Platform:

* The Personal Communication Platform allows a researcher:

    * To engage in (certain) social media networks directly from their pod.  This seems like a logical first step to start adding communication features to the pod that initially focuses on Personal Web Observatory features.

        * Essential standards: MicroPub/ActivityPub, webmention.

        * Requires single user authentication.

    * To generate and publish artifacts within her pod.

        * Essential standards: HTML, Memento.

        * Optional Standards:[ Linked Data Platform](https://www.w3.org/TR/ldp/).

        * Requires single user authentication.

        * Requires the notion of private/public artifacts, which does not necessarily require Access Control Lists.

        * Memento versioning support for artifacts.

        * Visible timestamping of artifacts.

    * To interact with artifacts in other researcher’s pods, which includes other researchers interacting with the researcher’s pod.

        * Essential standards: Linked Data Notification, webmention, Open Annotation.

        * Requires multi-user authentication.

        * Requires Access Control Lists.

        * Requires annotation store to store interactions by researchers that do not have a personal pod. Could this be the artifact store?

## Researcher’s interaction with the Pod:

1. Researcher installs the Pod software on a personal web server or on an institutional hosting platform. Her personal domain is made to resolve to the pod.

2. First time setup of the Pod:

    1. Researcher creates personal account in the pod.

    2. The researcher provides account credentials to be used by connectors when polling portals.

        1. For portals that support OAuth, the Pod will redirect the user to the appropriate OAuth endpoint of the portal. Upon successful login, the portal will redirect the user back to the Pod with an OAuth token that the Pod can use for subsequent authentications. For eg, Twitter OAuth looks like:[ https://dev.twitter.com/oauth/3-legged](https://dev.twitter.com/oauth/3-legged)

        2. For portals that do not provide an API, the researcher provides a list of web identity URIs that may have to be crawled.

    3. How far back in time should the artifact tracker try to retrieve artifact information? Herbert: Good question. Likely going back in time will be a good selling point for the pod idea, i.e. it is a bootstrap mechanism and selling point.

3. Begin artifact monitoring.

    4. Periodically look for new artifacts in the listed portals.

        3. Should API lookup frequency and crawl frequency for these portals be configurable? Herbert: I think that could be part of the central information about connectors.

    5. Store event metadata about artifacts in the artifact event store:

        4. Retrieve metadata about the artifact.

        5. Render metadata as an AS2 event.

        6. Send AS2 payload to the pod’s Linked Data Notification Inbox.

        7. Recurrently read Inbox (observable pattern?).

        8. Deduplicate against metadata already in the database.

        9. Possibly augment metadata to meet access requirements. Herbert: We will need to look carefully at AS2 and see how we use it and possibly extend to meet access/output requirements.

        10. Ingest into database.

    6. Following ingest, extract and queue list of URIs for archiving. Herbert: If we do this with notifications a queue is not essential. But it might be safe to have one.

4. In the Pod’s homepage, the researcher can view the metadata retrieved, the crawl status, successes/failures of crawls, list of URIs to be archived, etc. Herbert - We need to think this through in terms of the distinction between the artifact tracker and the artifact event store. When it comes to accessing information, the focus should be on the latter. Information about the artifact tracker is management/log data and should not be in the AS2 database.

## Pod’s Workflow:

1. The installation process must manage all dependencies automatically, setup databases automatically, allocate necessary system resources/permissions, etc.

    1. Are Docker containers a possible candidate? The application will be very easy to port, and an institution can spawn multiple pods (one for each researcher) easily. Hard for a non-tech-savvy researcher to install their own pod? Herbert - Fair concern. Yet, one can imagine e.g. librarians, IT support to help with this. Also, maybe it could be made as easy as installing an app?

    2. Using a database for handling metadata may make it hard to migrate. Herbert - From conversations I have had, this should be possible. Complete virtual research environments are Docker-packaged and installed on multiple platforms.

2. During the setup process, create and store the researcher credentials for the pod, capture and store the 3rd party web portal’s login credentials, capture any other configuration parameters from the researcher.

    3. TODO: Research the best way to securely store and use these authentication information. Herbert - Can we get inspiration from e.g. 1Password?

3. Start monitoring:

    4. Spawn connectors to gather artifact metadata from web portals.

    5. Connectors add status, stats etc to the database. - Herbert: We need to think this through in terms of the distinction between the artifact tracker and the artifact event store. When it comes to accessing information, the focus should be on the latter. Information about the artifact tracker is management/log data and should not be in the AS2 artifact event store.

    6. Should connectors determine which resource URLs in an artifact must be added to the capture list? - Herbert: I think that is a function that should be taken care of by the artifact event component.

    7. Or, should crawlers bring back the artifact content and the Pod determines the URLs to be captured? - Herbert - the pod does only deal with metadata when it comes to materials discovered in portals.

    8. Crawlers return date artifact created, artifact uri, content? (depending on 3c or 3d above), author name, content portal, for comments, replies, etc the uri of the parent document, … - Herbert: See above. The artifact tracker communicates AS2 events via LDN to the artifact event store.

    9. Add artifact metadata to database. Herbert: See above re possible deduplication and augmentation of what is received in the Inbox.

        1. An artifact URI entered in the DB for the first time will not have any version information. Should we send Memento headers for this URI? Should all URIs have Memento enabled by default, even though they may never have a version? For eg, if the artifact is a tweet URI, then it will likely never have mementos. Should we send memento headers in this case? Herbert: I see no need to use Memento headers for the metadata records stored in the artifact event store. We can never be sure whether that metadata will never be updated.  I am interested in supporting Memento to access views of prior states of the artifact event store, e.g. which slide decks were in my pod 2 years ago?

        2. For artifact URIs that the database already has an entry for, add the newly observed URI as a version of the existing observation. Herbert - This needs consideration in light of deduplication. Heuristics will need to be used to determine whether something is a new version or a duplicate of already stored metadata.

        3. For CMS, GitHub, etc, the crawlers should find both the generic version-agnostic URI and the versioned URI. Herbert - The connectors should know which kind of system they are interacting with. The AS2 payload should reveal this kind of information. Similar with e.g. landing page, etc.

    10. Communicate with institutional archival process to identify artifacts that require archiving. Herbert - This could e.g. be done by sending the AS2 payload ingested in the database to the LDN Inbox of that process.

4. Build Pod’s homepage with artifact metadata, stats, reports, etc from db. Herbert - See above. The focus should be on artifact event metadata not on connector processes.

## Existing Tools:

A brief survey of some of the existing open-source tools that could potentially be extended to create the Pod are listed below.

### Bridgy: Candidate for Artifact Tracker

Bridgy pulls comments and likes from many web portals back to one’s personal website. It can also be used to publish posts from a personal website to other social networks. [https://brid.gy/](https://brid.gy/)

Notes:

* Bridgy crawl blogs and APIs of social sites periodically to check for updates related to a user.

* When accessing non-API resources such as blogs, bridgy uses [microformats2](http://microformats.org/wiki/microformats-2) to scrape the information it needs. Microformats is a way to "markup structured information in HTML", for scraping relevant information from a page. For example, bridgy looks for content in an HTML element with class name “p-summary” to use as the content of a tweet, it looks for the name of the author in an HTML class with name “p-name”, and so on.

* A publication platform must inform bridgy about new content using the webmention protocol, and bridgy will then grab the content and publish to the appropriate social network. The target url in the webmention should indicate which social network must be used to publish while the source URL is the content URL in the publication platform. All the major blogging platforms support webmentions.

Pros:

* Open source: [https://github.com/snarfed/bridgy](https://github.com/snarfed/bridgy)

* Python

* Uses a Database: [Google Cloud Datastore](https://cloud.google.com/appengine/docs/standard/python/datastore/)

* Supports [webmention protocol](https://www.w3.org/TR/webmention/)

* Uses [ActivityStreams](http://activitystrea.ms/)

* Authentication management with OAuth

* Code actively maintained.

Cons:

* This is kind of a middleman between a publication platform and social networks. So, this is not a publication platform and we will have to use one separately for the pod.

* Depends heavily on [Google App Engine SDK](https://cloud.google.com/appengine/downloads#Google_App_Engine_SDK_for_Python), where bridgy is hosted. Even uses proprietary Google Datastore for its database. We will need to fork this repo and separate the Google App Engine specific code, which means, we will not be able to actively take advantage of the updates to bridgy.

* No linked data support. So, support for Linked Data Notifications or LDP, etc will have to be implemented.

### Known: Candidate for Communication Platform, possibly for Artifact Event Store

An open publication platform that adheres to all the [indieweb](https://indieweb.org/Getting_Started) technologies and principles. [http://withknown.com/](http://withknown.com/)

Notes:

* This is a publication platform that integrates both the content publishing and content syndication to the social network (partially what bridgy does).

* This does not however periodically crawl or poll APIs for mentions or other references to the content.

* Provides plenty of plugins to syndicate content to 3rd party sites, including the Internet Archive: [http://docs.withknown.com/en/latest/plugins/community/](http://docs.withknown.com/en/latest/plugins/community/)

Pros:

* Opensource: [https://github.com/idno/Known](https://github.com/idno/Known)

* Uses MySQL database for storage. (can also use Mongo if preferred)

* Supports webmention, ActivityStreams, microformats, etc

* Code actively maintained

Cons:

* No linked data support. So, no support for Linked Data Notifications or LDP.

* Meant to be a collaborative multi-user system. Hence contains elaborate user management and interaction features.

* Written in PHP.

### Marmotta: Candidate for Artifact Event Store and storage for Communication Platform

An open implementation of a Linked Data Platform to publish linked data.

Pros:

* Open source LDP implementation

* Multiple Database Storage options.

* SPARQL and LDPath querying support.

* Built in authentication for user management.

Cons:

* No Webmention or ActivityStream support. Will have to be implemented.

* Poor Memento support

* A very large and complex project, so may have a steep initial learning curve to start developing.

* Portability and creating light, dedicated pods may be complicated.

## Resources:

[IndieWeb](https://indieweb.org/Getting_Started): This site provides a lot of useful resources, lists all the protocols and tools necessary to publish on our own site and syndicate content elsewhere.

### Libraries:

OAuth: [https://github.com/snarfed/oauth-dropins](https://github.com/snarfed/oauth-dropins)

ActivityStreams: [https://github.com/snarfed/granary](https://github.com/snarfed/granary)

WebMention: [https://github.com/vrypan/webmention-tools](https://github.com/vrypan/webmention-tools)

How to securely Hash Passwords: [https://security.stackexchange.com/questions/211/how-to-securely-hash-passwords](https://security.stackexchange.com/questions/211/how-to-securely-hash-passwords)

How to Store Salt: [https://security.stackexchange.com/questions/17421/how-to-store-salt](https://security.stackexchange.com/questions/17421/how-to-store-salt)

Best practices on securely storing access tokens: [https://security.stackexchange.com/questions/41769/best-practices-on-securely-storing-access-tokens](https://security.stackexchange.com/questions/41769/best-practices-on-securely-storing-access-tokens)

Is it worth hashing passwords on the client side: [https://stackoverflow.com/questions/3715920/is-it-worth-hashing-passwords-on-the-client-side](https://stackoverflow.com/questions/3715920/is-it-worth-hashing-passwords-on-the-client-side)

Securely store Oauth token(s) in file: [https://stackoverflow.com/questions/7629288/securely-store-oauth-tokens-in-file](https://stackoverflow.com/questions/7629288/securely-store-oauth-tokens-in-file)
