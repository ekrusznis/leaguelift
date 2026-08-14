-- Free interactive fundraising games. Entry is deliberately independent of donations:
-- there is no contribution_id, amount, checkout session, or payment field on either table.
-- A donation never creates, improves, or multiplies a game entry.
create table fundraising_game (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    campaign_id uuid not null unique references campaign(id),
    created_by_user_id uuid not null references app_user(id),
    game_type text not null,
    title text not null,
    instructions text,
    prize_description text,
    max_entries integer,
    entries_per_person integer not null default 1,
    rows integer,
    cols integer,
    status text not null default 'DRAFT',
    winner_entry_id uuid,
    winner_selected_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fundraising_game_type_check check (game_type in ('BIG_GAME_SQUARES','BRACKET_CHALLENGE','PREDICTION_CHALLENGE','FREE_PRIZE_DRAWING','TRIVIA_CHALLENGE')),
    constraint fundraising_game_status_check check (status in ('DRAFT','OPEN','CLOSED')),
    constraint fundraising_game_max_entries_check check (max_entries is null or max_entries > 0),
    constraint fundraising_game_entries_per_person_check check (entries_per_person between 1 and 20),
    constraint fundraising_game_rows_check check (rows is null or rows between 1 and 26),
    constraint fundraising_game_cols_check check (cols is null or cols between 1 and 26)
);
create index fundraising_game_org_idx on fundraising_game(organization_id);

create table fundraising_game_entry (
    id uuid primary key,
    game_id uuid not null references fundraising_game(id) on delete cascade,
    display_name text not null,
    email text not null,
    selection_key text,
    selection_text text,
    is_winner boolean not null default false,
    created_at timestamptz not null default now(),
    constraint fundraising_game_entry_name_length_check check (char_length(display_name) between 1 and 120),
    constraint fundraising_game_entry_email_length_check check (char_length(email) between 3 and 254),
    constraint fundraising_game_entry_selection_text_length_check check (selection_text is null or char_length(selection_text) <= 1000)
);
create index fundraising_game_entry_game_idx on fundraising_game_entry(game_id, created_at);
create index fundraising_game_entry_email_idx on fundraising_game_entry(game_id, lower(email));
create unique index fundraising_game_entry_selection_unique
    on fundraising_game_entry(game_id, selection_key)
    where selection_key is not null;

alter table fundraising_game
    add constraint fundraising_game_winner_entry_fk
    foreign key (winner_entry_id) references fundraising_game_entry(id);
