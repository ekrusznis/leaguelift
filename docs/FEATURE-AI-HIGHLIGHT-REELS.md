# Rally26 AI Highlights — Design Review Brief

## Objective

Design an AI-powered highlight creation system inside Rally26 that allows parents, coaches, team staff, and approved users to upload game videos and automatically create:

* Team highlights
* Player-specific highlights
* Skill/play-specific highlights
* Tournament recap videos
* Season highlight reels
* Social-media-ready clips
* Recruiting-style reels

The goal is not initially to compete directly with Hudl or Veo as a full-game analysis platform.

The differentiator should be:

> **Multiple people record the game. Rally26 intelligently organizes those videos into highlights.**

---

# Core Product Concept

Each Rally26 game or tournament should include a shared **Media / Highlights** area.

Users can upload:

* Short video clips
* Multiple clips from the same play
* Eventually full-game video

Rally26 associates uploads with:

* Organization
* Team
* Game
* Tournament/event
* Athlete(s)
* Play type
* Timestamp/game segment

The system then uses AI to classify, organize, and eventually generate highlight reels automatically.

---

# Primary Differentiator: CrowdSync

Rally26 should eventually recognize when several users recorded the same play.

Example:

Three parents record the same volleyball rally from different parts of the gym.

Instead of storing three unrelated videos, Rally26 creates:

## Play Event

Rally26 12U vs XYZ Volleyball
Set 2
Score: 18–16

Player: #12 Emma
Event: Kill

Available footage:

* Camera angle 1
* Camera angle 2
* Camera angle 3

AI should eventually:

* Determine that the videos represent the same play
* Group them together
* Choose the best camera angle
* Allow alternate-angle viewing
* Optionally create multi-angle edits

Working feature name:

**CrowdSync**

Core positioning:

> Everyone records the game. Rally26 puts the game back together.

---

# Recommended Development Phases

## Phase 1 — Smart Media Library

Do not begin with complex computer vision.

Users upload short videos to a specific:

* Game
* Tournament
* Practice
* Team event

Uploader manually selects:

* Athlete(s)
* Play type
* Optional description

Example volleyball tags:

* Serve
* Ace
* Attack
* Kill
* Block
* Dig
* Set
* Rally
* Celebration

System capabilities:

* Video upload
* Video compression/transcoding
* Thumbnail generation
* Clip trimming
* Athlete tagging
* Event tagging
* Search/filter
* Player media library
* Team media library
* Game media library

Allow Rally26 to automatically create basic reels from tagged clips.

---

# Phase 2 — AI-Assisted Tagging

AI analyzes uploaded short clips and suggests:

* Athlete
* Jersey number
* Play type
* Relevant moment
* Beginning/end of highlight

Example:

> Rally26 detected:
> #14 Mia
> Volleyball Kill

User chooses:

* Confirm
* Change player
* Change event
* Ignore

Human confirmation should be retained as labeled data that can improve future detection.

---

# Phase 3 — Athlete Detection

Rally26 should begin detecting athletes automatically.

Inputs available to the AI may include:

* Team roster
* Jersey number
* Team uniform
* Known athlete photos where legally permitted
* Previous confirmed clips

Preferred initial method:

**Jersey-number detection and player tracking**

Avoid relying exclusively on facial recognition.

System should be capable of:

* Detecting jersey numbers
* Tracking athletes during clips
* Associating athletes with plays
* Identifying clips featuring a selected athlete

Example:

> Rally26 found 7 new clips containing #8 Ava.

---

# Phase 4 — Automatic Play Recognition

Develop sport-specific event detection.

Start with a limited number of highly recognizable events.

## Volleyball

Possible first events:

* Serve
* Ace
* Attack
* Kill
* Block
* Dig
* Set
* Long rally

## Soccer

* Goal
* Shot
* Save
* Assist
* Tackle

## Basketball

* Made basket
* Three-pointer
* Block
* Steal
* Assist

## Baseball / Softball

* Hit
* Double
* Triple
* Home run
* Strikeout
* Catch
* Defensive play

## Football

* Touchdown
* Catch
* Run
* Sack
* Interception
* Big defensive play

Detection should use multiple signals where possible:

* Video action recognition
* Player movement
* Ball movement
* Scoreboard changes
* Whistles
* Crowd reaction
* Cheering
* Audio spikes
* User-confirmed training examples

---

# Phase 5 — CrowdSync

This should become a signature Rally26 feature.

System attempts to align uploads from the same game using:

* Upload metadata
* Recording timestamps
* Audio synchronization
* Visual similarity
* Scoreboard recognition
* Game clock
* Court/field positioning

When multiple clips match:

Create one shared **Game Event**.

Example data model:

GameEvent

* game_id
* timestamp
* period/set/inning
* score
* event_type
* athletes
* primary_clip
* alternate_clips
* confidence_score

AI chooses a preferred clip based on:

* Visibility
* Stability
* Resolution
* Angle
* Obstruction
* Audio quality

Users can still manually choose another angle.

---

# Phase 6 — Full Game AI

Only pursue this after short-clip detection is reliable.

Users may upload:

* Full volleyball match
* Full soccer match
* Full basketball game
* etc.

Example request:

> Create Ava's tournament highlights.

AI scans the full footage and attempts to:

1. Identify Ava
2. Track Ava
3. Detect important events
4. Clip the relevant moments
5. Rank the strongest plays
6. Build a reel automatically

This should be considered an advanced feature rather than an MVP requirement.

---

# AI Reel Generator

Users should be able to select:

## Player Reel

Example:

Ava — 14U Volleyball

Filters:

* This game
* Tournament
* Last 30 days
* Season
* Specific skill

Example:

> Create a reel of Ava's kills from this tournament.

---

## Team Reel

Examples:

* Game highlights
* Tournament recap
* Season recap
* Championship run

---

## Social Reel

Automatic formats:

* 15 seconds
* 30 seconds
* 60 seconds

Aspect ratios:

* 9:16
* 1:1
* 16:9

Possible additions:

* Team logo
* Player name
* Jersey number
* Score
* Rally26 branding
* Music
* Transitions

---

## Recruiting Reel

Longer, cleaner format.

Characteristics:

* Minimal effects
* Athlete identification before the play
* Longer lead-in to each event
* Preserve game context
* Optional athlete information
* Downloadable/shareable link

---

# Example User Experience

Parent opens Rally26 Monday after a tournament.

Notification:

> Ava's Rally26 Weekend
> 17 Highlights Found

Saturday:

* 6 Kills
* 3 Blocks
* 2 Aces

Sunday:

* 4 Kills
* 1 Block
* 1 Ace

Actions:

* Watch Highlights
* Create 30-second Reel
* Create 60-second Reel
* Create Tournament Reel
* Create Recruiting Reel

---

# Player Highlight Profile

Each athlete may eventually have a private highlight area.

Example:

## Ava Smith

14U Volleyball
#8

Season statistics or detected media:

* 41 clips
* 18 kills
* 9 blocks
* 7 aces
* 7 other highlights

Collections:

* Best Plays
* Tournament Highlights
* Serving
* Attacking
* Defense
* Season Reel

Visibility must be controlled by guardians and organization permissions.

---

# Club-Level Features

Club administrators should be able to generate:

* Organization highlight reels
* Tournament recaps
* Team social posts
* Player-of-the-week clips
* Sponsor content

Possible feature:

## Rally26 Moment of the Match

AI identifies several candidate highlights.

Team or fans vote.

Winning highlight becomes:

> Rally26 Moment of the Match
> Presented by [Sponsor]

This creates opportunities for sponsorship revenue.

---

# Viral / Marketing Loop

Highlight sharing should help grow Rally26.

Example:

Parent shares highlight externally.

Video includes subtle branding:

> Created with Rally26

Viewer follows link.

Viewer may be:

* Another parent
* Coach
* Club director
* Tournament operator

This creates an organic acquisition loop.

---

# Monetization Consideration

Basic media functionality may be included with Rally26 organization subscriptions.

Advanced AI features could become:

## Rally26 Highlights+

Potential parent-level subscription.

Possible features:

* AI athlete detection
* Automatic highlight discovery
* Unlimited reels
* Season archive
* Recruiting exports
* Advanced editing
* Additional storage

The club acquires the organization.

The organization brings the families.

Families optionally purchase premium highlight features.

---

# Privacy and Youth Safety Requirements

This system involves minors and must be designed accordingly.

Parent/guardian should control:

* Athlete profile
* Media permissions
* Highlight visibility
* Public sharing
* Download permissions
* AI identification permissions
* Data deletion

Possible visibility levels:

### Private

Parent / athlete household only

### Team

Approved team members and families

### Club

Organization members

### Public

Externally shareable

Public sharing should require explicit permission.

---

# Facial Recognition

Do not make facial recognition a dependency of the MVP.

Prefer:

* Jersey-number recognition
* Uniform recognition
* Player tracking
* Manual roster confirmation

If biometric or facial recognition is ever added, it must have explicit consent and appropriate legal/privacy review.

---

# Technical Architecture Areas to Evaluate

The planning agent should determine architecture for:

## Video Infrastructure

* Upload service
* Cloud object storage
* Video transcoding
* CDN delivery
* Thumbnail generation
* Clip extraction
* Video compression
* Streaming

## AI Processing Pipeline

Potential stages:

Upload

↓

Transcode

↓

Frame sampling

↓

Player detection

↓

Object tracking

↓

Jersey number OCR

↓

Sport event detection

↓

Audio analysis

↓

Highlight scoring

↓

Event grouping

↓

Metadata generation

↓

Reel generation

---

# Possible Data Objects

Evaluate data models similar to:

### MediaAsset

* id
* uploader_id
* organization_id
* team_id
* game_id
* athlete_ids
* storage_url
* duration
* timestamp
* status
* visibility

### DetectedEvent

* media_asset_id
* event_type
* athlete_ids
* start_time
* end_time
* confidence
* confirmed

### GameEvent

Represents a real-world play.

May reference multiple MediaAssets.

### HighlightReel

* owner
* athlete/team
* source_events
* format
* duration
* visibility
* export_status

---

# Processing Cost Considerations

The architecture must model cost before offering unlimited AI.

Important cost drivers:

* Video storage
* CDN bandwidth
* Transcoding
* AI inference
* OCR
* Object tracking
* Video rendering
* Long-game processing

Planning should estimate cost per:

* 30-second upload
* 5-minute upload
* Full match
* Generated highlight reel

Rally26 should enforce upload and processing limits based on subscription tier if necessary.

---

# MVP Definition

The initial MVP should NOT require automatic full-game sports understanding.

Recommended MVP:

### Upload

Parents upload short clips.

### Associate

Clip belongs to game/team.

### Tag

Parent selects athlete and event.

### AI Assist

AI suggests tag and clip boundaries.

### Organize

Rally26 builds athlete and team libraries.

### Generate

Users create:

* Player highlight reel
* Team highlight reel
* Social reel

### Share

Private or authorized public link.

This provides immediate user value while establishing the dataset necessary for future AI automation.

---

# Long-Term Vision

Rally26 should eventually be able to receive many recordings from a sporting event and reconstruct meaningful moments from them.

Example:

20 parents record portions of a volleyball tournament.

Rally26 automatically understands:

* Which match each recording belongs to
* When each recording occurred
* Which players appear
* What plays happened
* Which recordings captured the same play
* Which angle is best
* Which moments are worth saving

Then Rally26 automatically creates:

* Athlete highlight reels
* Match highlights
* Tournament recaps
* Club promotional content
* Recruiting footage
* Social media clips

The strategic vision is:

> **Rally26 transforms the hundreds of videos already being recorded at youth sporting events into organized, searchable, shareable sports memories.**

The technical design should prioritize an incremental path from manually tagged short clips to increasingly automated event and athlete recognition rather than attempting full autonomous sports-video analysis in version one.
