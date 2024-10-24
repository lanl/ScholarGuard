# Portal Requirements

For each of the tracker portals there are variables that are required to run the trackers.
For example, Slideshare requires both an `apiKey` and `apiSecret`, while Figshare only requires each user to have a `userId`.
This document is present to show the optional and non-optional variables for the portal tracker for which we currently have implementations.

# Variables

A list of the various variables used by different trackers is as follows:

- `portal_name`: name of the portal tracker to use
- `users`: a list of users defined by:
    - `id`: identifier of the user (ORCID)
    - `username`: username for the portal specified
    - `userId`: user id for the portal specified
    - `portalUrl`: url a user performs activities on (e.g. a blog website http://ws-dl.blogspot.com)
    - `apiKey`: api key to be used with a portal api
    - `apiSecret`: api secret to be used with a portal api
    - `oauthToken`: OAUTH token used to interact with OAUTH APIs (i.e Twitter)
    - `oauthSecret`: OAUTH secret used to interact with OAUTH APIs (i.e Twitter)
    - `eventBaseUrl`: the base URL where an event id will be appended and where an event can be found (e.g. http://myresearch.institute/tracker/event/ is provided and becomes http://myresearch.institute/tracker/event/id123151)
    - `lastTracked`: datetime of the last time a tracker for the specified portal was executed
    - `lastToken`: last token returned by an api for a specified portal
- `ldn_inbox_url`: the inbox that receives the resulting AS2 message from a tracker process

# Portals

Required variables based on portal.
Common required variables are:

- `portal_name`
- `users`
    - `id`
    - `lastTracked`
    - `eventBaseUrl`
- `ldn_inbox_url`

## Blogger

- `portal_name`
- `users`
    - `id`
    - `lastTracked`
    - `userId`
    - `apiKey`
    - `portalUrl`
    - `eventBaseUrl`
- `ldn_inbox_url`

## Figshare

- `portal_name`
- `users`
    - `username`
    - `userId`
    - `eventBaseUrl`
- `ldn_inbox_url`

## Github

- `portal_name`
- `users`
    - `id`
    - `lastTracked`
    - `username`
    - `apiKey`
    - `apiSecret`
    - `eventBaseUrl`
    - `lastToken` (**optional**)
- `ldn_inbox_url`

## Hypothesis

- `portal_name`
- `users`
    - `id`
    - `lastTracked`
    - `username`
    - `eventBaseUrl`
- `ldn_inbox_url`

## Medium

- `portal_name`
- `users`
    - `id`
    - `lastTracked`
    - `username`
    - `eventBaseUrl`
- `ldn_inbox_url`

## Personal websites

- `portal_name`
- `users`
    - `id`
    - `lastTracked`
    - `portalUrl`
    - `eventBaseUrl`
- `ldn_inbox_url`

## Publons

- `portal_name`
- `users`
    - `id`
    - `lastTracked`
    - `username`
    - `userId`
    - `apiKey`
    - `eventBaseUrl`
- `ldn_inbox_url`

## Slideshare

- `portal_name`
- `users`
    - `id`
    - `lastTracked`
    - `username`
    - `apiKey`
    - `apiSecret`
    - `eventBaseUrl`
- `ldn_inbox_url`

## Stackoverflow

- `portal_name`
- `users`
    - `id`
    - `lastTracked`
    - `username`
    - `apiKey`
    - `apiSecret`
    - `eventBaseUrl`
- `ldn_inbox_url`

## Twitter

- `portal_name`
- `users`
    - `id`
    - `lastTracked`
    - `username`
    - `apiKey`
    - `apiSecret`
    - `oauthToken`
    - `oauthSecret`
    - `eventBaseUrl`
- `ldn_inbox_url`

## Wikipedia

- `portal_name`
- `users`
    - `id`
    - `lastTracked`
    - `username`
    - `eventBaseUrl`
- `ldn_inbox_url`

## Wordpress

- `portal_name`
- `users`
    - `id`
    - `lastTracked`
    - `username`
    - `portalUrl`
    - `eventBaseUrl`
- `ldn_inbox_url`
