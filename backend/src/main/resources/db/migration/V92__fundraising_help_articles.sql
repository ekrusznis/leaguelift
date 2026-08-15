-- Phase 5B: Fundraising Help Center coverage.
-- Public audience is deliberate: these articles describe product behavior and may be
-- shared with volunteer organizers/supporters without exposing organization-private data.

insert into support_article
(id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
(
 '92000000-0000-4000-8000-000000000001',
 'creating-a-rally26-fundraiser',
 'Creating a Rally26 fundraiser',
 'Choose a fundraiser template, set the goal and dates, save a draft, and prepare it for activation.',
 $body$
## Who can create a fundraiser

Organization owners and authorized administrators can create fundraisers. Assigned coaches and linked guardians may also create fundraisers within the organization and team access Rally26 gives them.

## Choose a template

Start with the template that best matches what you are doing. Rally26 supports general fundraising, in-person events, sponsor matches, milestone challenges, team or family fundraising challenges, and starter presets such as bake sales and car washes.

## Set the basics

Enter a clear fundraiser name and description, a fundraising goal, and optional team scope. Choose a start date and either a **7 day**, **30 day**, **90 day**, or custom end date. In-person event templates can also include a venue or location name and address.

## Save or submit

Saving keeps the fundraiser in **Draft** until you are ready. When you request activation, Rally26 follows the organization's owner-approval setting. The public QR code and sharing tools are available from the fundraiser once the campaign is ready to share.
$body$,
 'Fundraising', 'PUBLIC', 'PUBLISHED', 10, now()
),
(
 '92000000-0000-4000-8000-000000000002',
 'fundraiser-owner-approval',
 'How fundraiser owner approval works',
 'Understand when a fundraiser needs owner approval and what happens after a coach or guardian submits one.',
 $body$
## The owner controls the policy

Each organization owner controls whether owner approval is required before a fundraiser becomes active. The setting applies across the organization.

## When approval is required

A coach, guardian, or other permitted creator builds the fundraiser as a **Draft** and selects the activation action when it is ready. Rally26 changes it to **Pending approval** and adds an approval item for the owner. The owner also receives a notification email.

The owner can approve the fundraiser or return it to draft for changes. Returning it to draft does not delete the fundraiser; the creator can edit it and submit it again.

## After approval

If the start date is today or earlier, approval makes the fundraiser **Active**. If the start date is in the future, it becomes **Scheduled** and Rally26 activates it automatically when the start date arrives.

## When approval is not required

A permitted creator can request activation without waiting for an owner decision. Owner-only closeout and archive controls still remain with the organization owner.
$body$,
 'Fundraising', 'PUBLIC', 'PUBLISHED', 20, now()
),
(
 '92000000-0000-4000-8000-000000000003',
 'fundraiser-statuses-and-dates',
 'Fundraiser statuses, start dates, and end dates',
 'What Draft, Pending approval, Scheduled, Active, Ended, Closed, and Archived mean in Rally26.',
 $body$
## Draft

The fundraiser is still being prepared and is not active publicly.

## Pending approval

The fundraiser was submitted under an organization that requires owner approval. The owner must approve it or return it to draft.

## Scheduled

The fundraiser is approved but has a future start date. Rally26 activates it automatically when that date arrives.

## Active

The fundraiser is in its fundraising window and its public page is live. Online contribution availability depends on the fundraiser type and Rally26's payment-safety rules.

## Ended

The configured end date has passed. Rally26 ends the fundraising window automatically. An ended fundraiser remains available to the owner for review and closeout.

## Closed

The organization owner has completed fundraiser closeout. Closing is an explicit owner action; Rally26 does not automatically close a fundraiser just because its end date passed.

## Archived

The fundraiser is retained for history but is no longer part of the active working list.
$body$,
 'Fundraising', 'PUBLIC', 'PUBLISHED', 30, now()
),
(
 '92000000-0000-4000-8000-000000000004',
 'sharing-a-fundraiser-qr-link-flyer',
 'Sharing a fundraiser with QR codes, links, and flyers',
 'Use a fundraiser public link, QR code, native sharing tools, or a printable flyer to reach supporters.',
 $body$
## Public fundraiser link

Every shareable fundraiser has a public Rally26 URL. Supporters can open that link without joining the organization or installing the Rally26 app.

## QR code

Rally26 creates a QR code that points to the fundraiser's public URL. Because the QR points to the fundraiser rather than to a temporary checkout session, the same code can stay on signs and handouts while fundraiser details are updated.

## Mobile sharing

In the Rally26 mobile app, use the fundraiser's Share action to open your phone or tablet's native sharing options. You can send the public link through the apps already available on your device.

## Printable flyer

On the web, open the fundraiser flyer and use your browser's Print command. You can print it on paper or choose **Save as PDF** when your browser or operating system provides that option.
$body$,
 'Fundraising', 'PUBLIC', 'PUBLISHED', 40, now()
),
(
 '92000000-0000-4000-8000-000000000005',
 'running-an-in-person-fundraiser',
 'Running an in-person fundraiser',
 'Set up bake sales, car washes, clinics, restaurant nights, and other location-based fundraising events.',
 $body$
## Create the event fundraiser

Choose an in-person event template or one of its starter presets, such as a bake sale or car wash. Add the event details supporters need, including the fundraiser dates, venue or location name, address, and a short description of what is happening.

## Put the QR code where people can see it

The fundraiser's public QR code works well on a table sign, poster, check-in desk, or printed flyer. A supporter can scan it and open the public fundraiser page on their own phone.

## Payments

Ordinary in-person fundraisers that are not games of chance may use Rally26's enabled online contribution flow. If an organization also records cash, check, or other externally received support, those records remain separate from the public online checkout and must follow the organization's normal financial-verification process.
$body$,
 'Fundraising', 'PUBLIC', 'PUBLISHED', 50, now()
),
(
 '92000000-0000-4000-8000-000000000006',
 'free-fundraising-games',
 'Free fundraising games and challenges',
 'How Big Game Squares, brackets, predictions, prize drawings, and trivia stay separate from donations.',
 $body$
## Free means free to enter

Rally26 promotional games are designed so participation is separate from fundraising. A supporter does **not** need to donate, purchase anything, or make any payment to enter a game that Rally26 labels free to enter.

A donation does not provide extra entries, improve the participant's odds, unlock a better square or position, or change prize eligibility.

## Supported game styles

Depending on the fundraiser, Rally26 may offer **Big Game Squares**, **Bracket Challenges**, **Prediction Challenges**, **Free Prize Drawings**, or **Trivia Challenges**. The fundraiser raises support for the organization; the game provides a separate way to participate and engage with the event.

## Online contributions may be unavailable

Rally26 may disable online contribution checkout on a fundraiser that has a promotional game attached. This is intentional payment-safety behavior and does not prevent free game entry.

## Do not turn a free game into a paid game

An organizer may not require cash, check, Venmo, a donation, or any other payment to obtain a free-game entry. Changing the payment method does not change the free-entry rule.
$body$,
 'Fundraising', 'PUBLIC', 'PUBLISHED', 60, now()
),
(
 '92000000-0000-4000-8000-000000000007',
 'fundraising-payment-and-promotion-safety',
 'Fundraising payment and promotion safety',
 'Why payment options can differ by fundraiser and what organizers must not treat as a game entry payment.',
 $body$
## Ordinary fundraising

General donations, travel funds, equipment funds, in-person events, sponsor matches, and similar non-random fundraising may use Rally26's enabled online payment flow when the campaign is eligible for it.

## Promotional games

Free-entry games are not paid fundraising entries. Rally26 keeps game entries separate from contribution records and may disable online contribution checkout for a fundraiser with a promotional game attached.

## Manual or external payments

Recording an externally received donation does not create or buy a game entry. Cash, check, Venmo, or another external payment method must never be used as a workaround to charge for participation in a Rally26 game that is designated free to enter.

## Regulated raffle or wagering activity

Do not use Rally26's ordinary fundraising or free-game tools to run paid 50/50 raffles, paid sports pools, lottery-style ticket sales, wagering, or another regulated paid-entry game unless Rally26 has expressly enabled that specific use after the required legal, licensing, and payment-provider review.
$body$,
 'Fundraising', 'PUBLIC', 'PUBLISHED', 70, now()
),
(
 '92000000-0000-4000-8000-000000000008',
 'ending-closing-and-archiving-a-fundraiser',
 'Ending, closing, and archiving a fundraiser',
 'What happens when a fundraiser reaches its end date and which closeout actions remain with the owner.',
 $body$
## Automatic end date

An active fundraiser remains open through its configured end date. On the next lifecycle check after that date, Rally26 changes it to **Ended**. If a free fundraising game is still open, Rally26 closes that game with the fundraiser.

## Owner closeout

Ended does not mean deleted. The organization owner receives a closeout action so they can review the fundraiser before choosing **Close**. Contribution history, audit history, and reporting records remain available according to Rally26's retention rules.

## Archive

After closeout, the owner can archive the fundraiser when it no longer needs to appear in the normal working list. Archive is a history/organization state, not a refund or deletion action.
$body$,
 'Fundraising', 'PUBLIC', 'PUBLISHED', 80, now()
);
