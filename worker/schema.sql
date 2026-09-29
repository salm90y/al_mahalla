-- D1 Database Schema for Cloudflare ps1_db (Domain: ahmed1986y.com)
-- Tables: users, sessions, private_messages, friendships, friend_requests, conversations

CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT UNIQUE NOT NULL,
    email TEXT UNIQUE,
    password_hash TEXT NOT NULL,
    avatar_url TEXT DEFAULT '',
    status TEXT DEFAULT 'offline',
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    last_seen INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    livekit_identity TEXT
);

CREATE TABLE IF NOT EXISTS sessions (
    token TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    expiry INTEGER NOT NULL,
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS private_messages (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    receiver_id TEXT NOT NULL,
    type TEXT DEFAULT 'text',
    content TEXT NOT NULL,
    media_url TEXT DEFAULT '',
    file_name TEXT DEFAULT '',
    file_size INTEGER DEFAULT 0,
    duration INTEGER DEFAULT 0,
    location_lat REAL DEFAULT 0.0,
    location_lng REAL DEFAULT 0.0,
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    is_read INTEGER DEFAULT 0,
    is_delivered INTEGER DEFAULT 1,
    FOREIGN KEY(sender_id) REFERENCES users(id),
    FOREIGN KEY(receiver_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS friendships (
    id TEXT PRIMARY KEY,
    user1_id TEXT NOT NULL,
    user2_id TEXT NOT NULL,
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    is_blocked INTEGER DEFAULT 0,
    blocked_by TEXT DEFAULT '',
    mute_until INTEGER DEFAULT 0,
    mute_type TEXT DEFAULT 'none',
    FOREIGN KEY(user1_id) REFERENCES users(id),
    FOREIGN KEY(user2_id) REFERENCES users(id),
    UNIQUE(user1_id, user2_id)
);

CREATE TABLE IF NOT EXISTS friend_requests (
    id TEXT PRIMARY KEY,
    from_user_id TEXT NOT NULL,
    to_user_id TEXT NOT NULL,
    status TEXT DEFAULT 'pending',
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    updated_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    FOREIGN KEY(from_user_id) REFERENCES users(id),
    FOREIGN KEY(to_user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS conversations (
    id TEXT PRIMARY KEY,
    user1_id TEXT NOT NULL,
    user2_id TEXT NOT NULL,
    last_message_id TEXT DEFAULT '',
    last_message_text TEXT DEFAULT '',
    last_message_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    unread_count_user1 INTEGER DEFAULT 0,
    unread_count_user2 INTEGER DEFAULT 0,
    UNIQUE(user1_id, user2_id)
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_messages_conv ON private_messages(conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_friendships_users ON friendships(user1_id, user2_id);
CREATE INDEX IF NOT EXISTS idx_requests_to ON friend_requests(to_user_id, status);
CREATE INDEX IF NOT EXISTS idx_requests_from ON friend_requests(from_user_id, status);

-- Seed Default Admin Account (User: ahmed | Pass: 123456)
INSERT OR IGNORE INTO users (id, username, email, password_hash, avatar_url, status, created_at, last_seen, livekit_identity)
VALUES (
    'u_ahmed_1986',
    'ahmed',
    'ahmed1986y5@gmail.com',
    'b5e7d733d525d44fa4b43d6f554a8d30eb575d3df05b7b8b8446bee2249d711c',
    'https://api.ahmed1986y.com/media/avatars/ahmed.jpg',
    'online',
    1741478400000,
    1741478400000,
    'u_ahmed_1986'
);

-- TV Channels Table for Cloudflare D1
CREATE TABLE IF NOT EXISTS tv_channels (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    title TEXT NOT NULL,
    category TEXT DEFAULT 'قنوات فضائية',
    logo_url TEXT DEFAULT '',
    stream_url TEXT NOT NULL,
    epg_id TEXT DEFAULT '',
    is_live INTEGER DEFAULT 1,
    sort_order INTEGER DEFAULT 0,
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
);

CREATE INDEX IF NOT EXISTS idx_tv_channels_name ON tv_channels(name);
CREATE INDEX IF NOT EXISTS idx_tv_channels_title ON tv_channels(title);
CREATE INDEX IF NOT EXISTS idx_tv_channels_cat ON tv_channels(category);

-- Seed Channels from M3U IPTV Provider (maxshowplayer.site)
INSERT OR IGNORE INTO tv_channels (id, name, title, category, logo_url, stream_url, sort_order) VALUES
('ch_bein_news', 'beIN SPORTS News', 'beIN SPORTS الإخبارية المفتوحة HD', 'قنوات رياضية', 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/501.m3u8', 1),
('ch_bein_1', 'beIN SPORTS 1', 'beIN SPORTS 1 HD Premium', 'قنوات رياضية', 'https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/502.m3u8', 2),
('ch_bein_2', 'beIN SPORTS 2', 'beIN SPORTS 2 HD', 'قنوات رياضية', 'https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/503.m3u8', 3),
('ch_bein_3', 'beIN SPORTS 3', 'beIN SPORTS 3 HD', 'قنوات رياضية', 'https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/504.m3u8', 4),
('ch_ssc_1', 'SSC 1 HD', 'قناة SSC الرياضية 1 HD', 'قنوات رياضية', 'https://images.unsplash.com/photo-1518091043644-c1d4457512c6?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/505.m3u8', 5),
('ch_alkass_1', 'Alkass 1 HD', 'قناة الكأس القطرية 1 HD', 'قنوات رياضية', 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/506.m3u8', 6),
('ch_ad_sports', 'Abu Dhabi Sports', 'قناة أبوظبي الرياضية 1 HD', 'قنوات رياضية', 'https://images.unsplash.com/photo-1518091043644-c1d4457512c6?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/507.m3u8', 7),
('ch_quran', 'القرآن الكريم مباشر', 'قناة القرآن الكريم (مكة المكرمة مباشر)', 'قنوات إسلامية', 'https://images.unsplash.com/photo-1591604129939-f1efa4d9f7fa?w=600&auto=format&fit=crop&q=80', 'https://win.holol.com/live/quran/playlist.m3u8', 8),
('ch_sunnah', 'السنة النبوية مباشر', 'قناة السنة النبوية (المدينة المنورة مباشر)', 'قنوات إسلامية', 'https://images.unsplash.com/photo-1584551246679-0daf3d275d0f?w=600&auto=format&fit=crop&q=80', 'https://win.holol.com/live/sunnah/playlist.m3u8', 9),
('ch_mbc_1', 'MBC 1 HD', 'قناة MBC 1 HD الرسمية', 'قنوات منوعة', 'https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/601.m3u8', 10),
('ch_mbc_masr', 'MBC مصر HD', 'قناة MBC مصر HD', 'قنوات منوعة', 'https://images.unsplash.com/photo-1518791841217-8f162f1e1131?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/602.m3u8', 11),
('ch_mbc_action', 'MBC Action HD', 'قناة MBC Action HD أفلام وحركة', 'قنوات ترفيهية', 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/603.m3u8', 12),
('ch_mbc_drama', 'MBC Drama HD', 'قناة MBC Drama HD مسلسلات', 'قنوات ترفيهية', 'https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/604.m3u8', 13),
('ch_jazeera', 'الجزيرة الإخبارية', 'قناة الجزيرة الإخبارية HD مباشر', 'قنوات إخبارية', 'https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=600&auto=format&fit=crop&q=80', 'https://live-hls-web-aje.akamaized.net/hls/live/2004245-b/aje/index.m3u8', 14),
('ch_arabiya', 'العربية الإخبارية', 'قناة العربية الإخبارية HD مباشر', 'قنوات إخبارية', 'https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/701.m3u8', 15),
('ch_hadath', 'الحدث مباشر', 'قناة الحدث الإخبارية HD', 'قنوات إخبارية', 'https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/702.m3u8', 16),
('ch_skynews', 'سكاي نيوز عربية', 'قناة سكاي نيوز عربية HD', 'قنوات إخبارية', 'https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/703.m3u8', 17),
('ch_natgeo', 'ناشيونال جيوغرافيك', 'ناشيونال جيوغرافيك أبوظبي HD', 'قنوات وثائقية', 'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/801.m3u8', 18),
('ch_rotana_cinema', 'روتانا سينما', 'قناة روتانا سينما HD - مش حتقدر تغمض عينيك', 'قنوات سينمائية', 'https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/901.m3u8', 19),
('ch_rotana_classic', 'روتانا كلاسيك', 'قناة روتانا كلاسيك زمان HD', 'قنوات سينمائية', 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/902.m3u8', 20),
('ch_osn_movies', 'OSN Movies', 'قناة OSN Movies Action HD', 'قنوات سينمائية', 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/903.m3u8', 21),
('ch_zee_alwan', 'زي ألوان', 'قناة زي ألوان HD دراما هندية ومدبلجة', 'قنوات ترفيهية', 'https://images.unsplash.com/photo-1518791841217-8f162f1e1131?w=600&auto=format&fit=crop&q=80', 'http://maxshowplayer.site:2052/live/13968296781874/20098269331298/904.m3u8', 22);

