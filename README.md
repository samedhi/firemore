# firemore

Get more from Firestore.

Firemore is an opinionated library for writing clojurescript applications using [Google Firestore](https://cloud.google.com/firestore). Main features include:

1. A binding between [Firestore db](https://firebase.google.com/docs/firestore) and your [re-frame](https://github.com/Day8/re-frame) application db. Use normal clojure map functions (`get`, `assoc`, et all) to read and write from your application db and have changes automatically persisted to Firestore.
1. Use [re-frame](https://github.com/Day8/re-frame) subscriptions to update your UI based upon changes to app-db. Use re-frame event handlers to update app-db (and implicitly Firestore).
1. Use [re-com](https://github.com/Day8/re-com) as your UI.
