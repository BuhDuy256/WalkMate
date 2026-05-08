import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ChevronLeft, History } from 'lucide-react';
import { BottomNav } from '../layout/BottomNav';
import { WalkResultPostCard, WalkPost } from '../walk/WalkResultPostCard';
import { VisibilityBottomSheet } from '../walk/VisibilityBottomSheet';
import { Visibility } from '../walk/VisibilityChip';

const INITIAL_POSTS: WalkPost[] = [
    {
        id: 1,
        authorName: 'Luân Trần',
        authorInitials: 'LT',
        avatarColor: '#F97316',
        caption: 'Great morning walk! Fresh air and met a new friend.',
        hotspot: 'Tao Dan Park',
        postedAt: '2 hours ago',
        duration: '28 min',
        distance: '2.4 km',
        points: 120,
        companionName: 'Nguyen Minh',
        visibility: 'public',
        showMap: true,
        showCompanion: true,
        showStats: true,
    },
    {
        id: 2,
        authorName: 'Luân Trần',
        authorInitials: 'LT',
        avatarColor: '#F97316',
        caption: 'Evening solo walk — loved the peace and quiet 🌙',
        hotspot: 'Ho Con Rua',
        postedAt: 'Yesterday',
        duration: '40 min',
        distance: '3.5 km',
        points: 180,
        companionName: undefined,
        visibility: 'friends',
        showMap: false,
        showCompanion: false,
        showStats: true,
    },
    {
        id: 3,
        authorName: 'Luân Trần',
        authorInitials: 'LT',
        avatarColor: '#F97316',
        caption: 'Quick walk around the block 🌿',
        hotspot: 'Le Van Tam Park',
        postedAt: '3 days ago',
        duration: '18 min',
        distance: '1.4 km',
        points: 60,
        companionName: 'Tran Linh',
        visibility: 'only_me',
        showMap: false,
        showCompanion: true,
        showStats: true,
    },
];

type Filter = 'all' | Visibility;

const FILTERS: { key: Filter; label: string }[] = [
    { key: 'all', label: 'All' },
    { key: 'public', label: 'Public' },
    { key: 'friends', label: 'Friends' },
    { key: 'only_me', label: 'Only me' },
];

export function MyWalkActivityScreen() {
    const navigate = useNavigate();
    const [posts, setPosts] = useState<WalkPost[]>(INITIAL_POSTS);
    const [filter, setFilter] = useState<Filter>('all');
    const [visSheetId, setVisSheetId] = useState<number | null>(null);

    const filtered = filter === 'all' ? posts : posts.filter((p) => p.visibility === filter);
    const sheetPost = posts.find((p) => p.id === visSheetId);

    function handleChangeVisibility(id: number) {
        setVisSheetId(id);
    }
    function handleDelete(id: number) {
        setPosts((prev) => prev.filter((p) => p.id !== id));
    }
    function handleSaveVisibility(id: number, newVis: Visibility) {
        setPosts((prev) => prev.map((p) => (p.id === id ? { ...p, visibility: newVis } : p)));
        setVisSheetId(null);
    }

    return (
        <>
            <div className="w-full h-full flex flex-col" style={{ background: '#FEF9F5' }}>
                {/* Header */}
                <div
                    className="flex items-center gap-3 px-5 pt-14 pb-4"
                    style={{
                        background: 'white',
                        borderBottom: '1px solid #F3F2F0',
                        position: 'sticky', top: 0, zIndex: 20,
                        boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
                    }}
                >
                    <button
                        onClick={() => navigate('/profile')}
                        style={{
                            width: 40, height: 40, background: '#F5F5F4',
                            borderRadius: 12, border: 'none', cursor: 'pointer',
                            display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                        }}
                    >
                        <ChevronLeft size={20} style={{ color: '#1C1917' }} strokeWidth={2.5} />
                    </button>
                    <h2
                        style={{
                            flex: 1, textAlign: 'center',
                            fontSize: 18, fontWeight: 800, color: '#1C1917', letterSpacing: '-0.3px',
                        }}
                    >
                        Walk Activity
                    </h2>
                    <div style={{ width: 40 }} />
                </div>

                {/* Filter chips */}
                <div
                    style={{
                        display: 'flex', gap: 8,
                        padding: '12px 20px 8px',
                        overflowX: 'auto',
                        background: 'white',
                        borderBottom: '1px solid #F3F2F0',
                    }}
                >
                    {FILTERS.map((f) => {
                        const active = filter === f.key;
                        return (
                            <button
                                key={f.key}
                                onClick={() => setFilter(f.key)}
                                style={{
                                    flexShrink: 0,
                                    padding: '7px 16px',
                                    borderRadius: 100,
                                    border: active ? 'none' : '1.5px solid #E7E5E4',
                                    background: active ? 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)' : 'white',
                                    color: active ? 'white' : '#78716C',
                                    fontSize: 13, fontWeight: 700,
                                    cursor: 'pointer',
                                    boxShadow: active ? '0 3px 10px rgba(249,115,22,0.28)' : 'none',
                                    transition: 'all 0.15s',
                                }}
                            >
                                {f.label}
                            </button>
                        );
                    })}
                </div>

                {/* List */}
                <div className="flex-1 overflow-y-auto mobile-scroll" style={{ padding: '16px 16px 88px' }}>
                    {filtered.length === 0 ? (
                        /* ── Empty state ── */
                        <div
                            style={{
                                background: 'white', borderRadius: 24,
                                border: '1.5px solid #F3F2F0',
                                boxShadow: '0 4px 16px rgba(0,0,0,0.06)',
                                padding: '40px 24px',
                                display: 'flex', flexDirection: 'column', alignItems: 'center',
                                marginTop: 16,
                            }}
                        >
                            <div
                                style={{
                                    width: 64, height: 64, borderRadius: '50%',
                                    background: '#FFF7ED', border: '2px solid #FED7AA',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    marginBottom: 18,
                                }}
                            >
                                <span style={{ fontSize: 30 }}>🚶</span>
                            </div>
                            <p style={{ fontSize: 17, fontWeight: 800, color: '#1C1917', marginBottom: 8, textAlign: 'center' }}>
                                No shared walks yet
                            </p>
                            <p style={{ fontSize: 13, color: '#78716C', textAlign: 'center', lineHeight: 1.55, marginBottom: 22 }}>
                                Post a completed walk from your History.
                            </p>
                            <button
                                onClick={() => navigate('/walk-history')}
                                style={{
                                    display: 'flex', alignItems: 'center', gap: 7,
                                    padding: '12px 28px',
                                    background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
                                    border: 'none', borderRadius: 100,
                                    fontSize: 14, fontWeight: 700, color: 'white',
                                    cursor: 'pointer',
                                    boxShadow: '0 6px 18px rgba(249,115,22,0.34)',
                                }}
                            >
                                <History size={15} strokeWidth={2.5} />
                                Go to History
                            </button>
                        </div>
                    ) : (
                        <div className="flex flex-col gap-3">
                            {/* Post count */}
                            <p style={{ fontSize: 12, color: '#A8A29E', fontWeight: 600, paddingLeft: 2 }}>
                                {filtered.length} post{filtered.length !== 1 ? 's' : ''}
                            </p>
                            {filtered.map((post) => (
                                <WalkResultPostCard
                                    key={post.id}
                                    post={post}
                                    variant="owner"
                                    onChangeVisibility={handleChangeVisibility}
                                    onDelete={handleDelete}
                                />
                            ))}
                        </div>
                    )}
                </div>
            </div>

            {/* Visibility sheet */}
            {visSheetId !== null && sheetPost && (
                <VisibilityBottomSheet
                    value={sheetPost.visibility}
                    onChange={(v) => handleSaveVisibility(visSheetId, v)}
                    onSave={() => { }}
                    onClose={() => setVisSheetId(null)}
                />
            )}

            <BottomNav />
        </>
    );
}
