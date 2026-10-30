# Marketing ideas

Low-cost ways to get Opson in front of people, in rough order of how much they
fit the app. Each one should pass a simple test: would it make someone feel
worse about food? If yes, skip it.

## 1. Recipe videos, recreated in Opson

Find recipe videos on Instagram and TikTok, recreate the recipe in
Opson as a public recipe, and reply on the video that it's in the app, with a
link. Or film our own recipe videos and send people to a link-in-bio page.

**Don't automate the comments.** A bot that posts comments breaks both
platforms' rules. TikTok's Community Guidelines ban spam and fake engagement,
including automated activity, and Instagram's Terms of Use forbid automated
access and spam. The likely result is a restricted or banned account, and the
creators whose videos we used would rightly be annoyed. Pasting the same
comment by hand on fifty videos a day gets flagged as spam too.

How to do it without spamming:

- **Ask the creator first.** A DM: "I loved your lentil soup and added it to
  Opson with credit to you. Mind if I link it in your comments?" A yes turns it
  into a small partnership. Many will reshare it.
- **Comment like a person.** A few a day, each one specific to the video,
  posted from an account that also posts its own content. Say who you are.
- **Credit every recipe.** Ingredient lists aren't copyrightable, but the
  creator's words, photos and video are. Link back, use our own photo or none,
  and write the steps in our own words.
- **Keep numbers off other people's videos.** No calorie counts in comments.
  Someone watching a pasta video didn't ask for one, and it's the exact thing
  Opson promises not to do. "The full recipe's in Opson, numbers optional" is
  fine.

For our own videos: short, cooking-first, and in the app's spirit: show the
food, then show logging it in No numbers or Macros only. No "what I eat in a
day" with low totals, no weigh-ins, no before and after. The link in bio goes to
a page on opsontracker.com that lists the recipes. The waitlist API already
takes a `source` value, so tag each channel (`tiktok`, `instagram`) and see
which one works.

One thing first: public recipes aren't built yet. Have 20 or so ready
before the first comment, so a click lands on something real.

## 2. Creator partnerships

Small creators (under about 50k followers) who already cook in a relaxed,
non-diet way. Look for food creators, intuitive eating dietitians, and
gym-adjacent cooks who don't push restriction. Offer:

- Their recipes featured in Opson with a creator credit and link.
- Free Pro for them, and a founding member link for their audience.
- A pay-per-video deal later, once something has shown it converts.

Write a one-page brief they can read in a minute: what Opson is, the four
modes, and what we won't say (no weight loss promises, no calorie targets, no
before and after). Review videos before they post, kindly.

## 3. The founding member offer as the hook

The pricing plan (`docs/plans/feedback-2026-10-29/pricing-and-pro.md`) gives
lifetime Pro to the first 1,000 accounts that log on 5 different days in their
first 30. It's a good hook because it rewards using the app, not just signing
up. "First 1,000 people who actually use it get Pro for life" works in a video
caption, a creator's code, or a Reddit reply. Check the final terms before
anyone promises it publicly.

## 4. Reddit, with care

Reddit can work if you show up as the founder with a story, not as an ad.

- **Read each subreddit's self-promotion rules first.** Many allow it only in
  set threads or not at all.
- **Fitness and tracking subs** (calorie counting, lose it, macro tracking, app
  recommendation subs, iOS and Android app subs): answer "what tracker should
  I use" threads honestly, say you made Opson, and mention what it doesn't do
  well yet.
- **"MyFitnessPal alternatives" threads** come up often. A specific reply about
  ranges and hiding calories will do better than a link.
- **Eating disorder recovery spaces: don't promote there.** Most recovery
  communities discourage tracking apps, often with good reason, and an app
  founder dropping a link is unwelcome. If a moderator or member invites you,
  or someone asks directly about trackers that hide numbers, answer plainly and
  point to their treatment team first.
- **The blog post** (`blog-why-i-built-opson.md`) is the best thing to share on
  Reddit, as a personal story, where the sub's rules allow it.

## 5. Recovery-informed messaging

The people who need Opson most are the easiest to hurt with the wrong ad.

- **Get the copy reviewed** by a registered dietitian who works in eating
  disorders before any campaign runs. The app already waits on a dietitian for
  its floors (`TODO(dietitian)`), so it may be the same person.
- **Dietitians as a channel.** Recovery dietitians often ask clients to log
  meals without numbers. No numbers mode plus the free provider summary PDF is
  a real tool for them. A short page for professionals and a few emails to
  practices could do more than any ad.
- **Words to keep out of marketing.** Lose, burn, guilt-free, clean,
  cheat day, before and after, any weight or low calorie number.

## 6. App Store search

The keyword field is 100 characters, and the current set is in
`docs/app-store/listing.md`. Ideas to test:

- Terms people search when they want less: "hide calories", "no calorie
  counting", "food journal", "meal diary", "intuitive eating".
- Apple doesn't allow competitor names in keywords, so "MyFitnessPal
  alternative" can't go there. It can go in a blog post title or a Reddit reply.
- The subtitle ("Food log with gentle numbers") does more for search than the
  keywords do. Try variants in App Store Connect's product page tests.
- Screenshots sell the difference: the same Today in All numbers and No numbers,
  side by side.

## 7. Other cheap channels

- **Product Hunt and Hacker News "Show HN"** at launch, led by the founder story.
- **Founder posts** on Threads or Bluesky about the design calls, such as why a
  big day never turns red. They're short, true and easy to share.
- **Press.** A pitch to health and tech writers built around the story and the
  modes. "Calorie app lets you hide the calories" is a headline someone might
  write.
