import { useState } from 'react';
import { useNavigate } from 'react-router';
import {
    Plus, RefreshCw, MapPin, Clock, Check, X,
    Sparkles, Star, Users, Zap, Search, ChevronRight,
} from 'lucide-react';
import { BottomNav } from '../layout/BottomNav';

// ─── Types ──────────────────────────────────────────────────────────────────

type SubTab = 'finding' | 'proposal' | 'session';

interface Proposal {
    id: number;
    name: string;
    age: number;
    initials: string;
    avatarColor: string;
    location: string;
    timeWindow: string;
    distanceAway: string;
    compatibility: number;
    bio: string;
}

interface WalkIntent {
    id: number;
    location: string;
    date: string;
    timeWindow: string;
    gender: string;
    ageRange: string;
    proposals: Proposal[];
    createdAt: string;
}

interface Session {
    id: number;
    partnerName: string;
    partnerInitials: string;
    partnerColor: string;
    partnerAge: number;
    compatibility: number;
    location: string;
    date: string;
    timeWindow: string;
    rating: number;
}

// ─── Mock Data ───────────────────────────────────────────────────────────────

const FINDING_INTENTS: WalkIntent[] = [
    {
        id: 1,
        location: 'Binh Quoi Village',
        date: '2026-05-03',
        timeWindow: '17:00 – 19:00',
        gender: 'Female',
        ageRange: '22–35',
        proposals: [],
        createdAt: '1d ago',
    },
    {
        id: 2,
        location: 'Nguyen Hue Walking Street',
        date: '2026-05-05',
        timeWindow: '18:00 – 20:00',
        gender: 'Any',
        ageRange: '18–40',
        proposals: [],
        createdAt: '3h ago',
    },
];

const PROPOSAL_INTENTS: WalkIntent[] = [
    {
        id: 3,
        location: 'Tao Dan Park',
        date: '2026-04-30',
        timeWindow: '07:00 – 09:00',
        gender: 'Any',
        ageRange: '18–40',
        createdAt: '5h ago',
        proposals: [
            {
                id: 31,
                name: 'Tran Linh',
                age: 28,
                initials: 'TL',
                avatarColor: '#0EA5E9',
                location: 'Tao Dan Park',
                timeWindow: '07:00 – 09:00',
                distanceAway: '1.2 km away',
                compatibility: 87,
                bio: 'Daily walker who loves nature and good conversation.',
            },
            {
                id: 32,
                name: 'Hoa Nguyen',
                age: 25,
                initials: 'HN',
                avatarColor: '#EC4899',
                location: 'Tao Dan Park',
                timeWindow: '07:30 – 09:00',
                distanceAway: '0.8 km away',
                compatibility: 84,
                bio: 'Early bird fitness walker, loves peaceful morning routes.',
            },
        ],
    },
    {
        id: 4,
        location: 'Le Van Tam Park',
        date: '2026-04-29',
        timeWindow: '16:00 – 18:00',
        gender: 'Female',
        ageRange: '20–32',
        createdAt: '2h ago',
        proposals: [
            {
                id: 41,
                name: 'Mai Anh',
                age: 23,
                initials: 'MA',
                avatarColor: '#8B5CF6',
                location: 'Le Van Tam Park',
                timeWindow: '16:30 – 18:00',
                distanceAway: '0.5 km away',
                compatibility: 91,
                bio: 'Loves evening walks and talking about life over fresh air.',
            },
        ],
    },
];

const SESSIONS: Session[] = [
    {
        id: 1,
        partnerName: 'Nguyen Minh',
        partnerInitials: 'NM',
        partnerColor: '#7C3AED',
        partnerAge: 25,
        compatibility: 92,
        location: 'Le Van Tam Park',
        date: '2026-04-29',
        timeWindow: '18:30 – 20:00',
        rating: 5,
    },
    {
        id: 2,
        partnerName: 'An Nguyen',
        partnerInitials: 'AN',
        partnerColor: '#059669',
        partnerAge: 27,
        compatibility: 88,
        location: 'Tao Dan Park',
        date: '2026-04-27',
        timeWindow: '07:00 – 08:30',
        rating: 5,
    },
];

// ─── Sub-components ──────────────────────────────────────────────────────────

function FindingTab({
    intents,
    onCancel,
    onNew,
}: {
    intents: WalkIntent[];
    onCancel: (id: number) => void;
    onNew: () => void;
}) {
    if (intents.length === 0) {
        return (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', paddingTop: '52px' }}>
                <div
                    style={{
                        width: '80px', height: '80px', borderRadius: '24px',
                        background: 'linear-gradient(135deg, #FFF7ED, #FFEDD5)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        marginBottom: '16px', boxShadow: '0 8px 24px rgba(249,115,22,0.12)',
                    }}
                >
                    <Search size={32} strokeWidth={1.6} style={{ color: '#F97316' }} />
                </div>
                <p style={{ fontSize: '17px', fontWeight: 700, color: '#1C1917', marginBottom: '6px' }}>No active searches</p>
                <p style={{ fontSize: '14px', color: '#A8A29E', textAlign: 'center', lineHeight: 1.5, marginBottom: '28px' }}>
                    Create a walk intent to start finding compatible walk partners nearby.
                </p>
                <button
                    onClick={onNew}
                    style={{
                        display: 'flex', alignItems: 'center', gap: '8px',
                        background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
                        border: 'none', borderRadius: '16px', padding: '14px 24px',
                        fontSize: '15px', fontWeight: 700, color: 'white', cursor: 'pointer',
                        boxShadow: '0 6px 20px rgba(249,115,22,0.38)',
                    }}
                >
                    <Plus size={16} strokeWidth={2.5} />
                    Create Walk Intent
                </button>
            </div>
        );
    }

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {intents.map((intent) => (
                <div
                    key={intent.id}
                    style={{
                        background: 'white', borderRadius: '20px', padding: '16px',
                        border: '1.5px solid #F3F2F0', boxShadow: '0 4px 14px rgba(0,0,0,0.05)',
                    }}
                >
                    {/* Top row */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
                        <div
                            style={{
                                width: '40px', height: '40px', background: '#FFF7ED',
                                borderRadius: '12px', display: 'flex', alignItems: 'center',
                                justifyContent: 'center', flexShrink: 0,
                            }}
                        >
                            <MapPin size={17} style={{ color: '#F97316' }} strokeWidth={2} />
                        </div>
                        <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ fontSize: '15px', fontWeight: 700, color: '#1C1917', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                {intent.location}
                            </div>
                            <div style={{ fontSize: '12px', color: '#A8A29E', marginTop: '1px' }}>{intent.date}</div>
                        </div>

                        {/* Searching badge */}
                        <div style={{ display: 'flex', alignItems: 'center', gap: '5px', background: '#FFF7ED', borderRadius: '20px', padding: '5px 10px', flexShrink: 0 }}>
                            <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#F97316', animation: 'wm-pulse 1.4s ease-in-out infinite' }} />
                            <span style={{ fontSize: '11px', fontWeight: 700, color: '#F97316' }}>Finding…</span>
                        </div>
                    </div>

                    {/* Details */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '14px', paddingLeft: '50px', marginBottom: '12px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                            <Clock size={12} style={{ color: '#A8A29E' }} />
                            <span style={{ fontSize: '12px', color: '#78716C', fontWeight: 500 }}>{intent.timeWindow}</span>
                        </div>
                        <span style={{ fontSize: '12px', color: '#A8A29E' }}>
                            {intent.gender} · {intent.ageRange}
                        </span>
                    </div>

                    {/* Footer */}
                    <div style={{ paddingLeft: '50px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <span style={{ fontSize: '12px', color: '#C9C5C1' }}>Created {intent.createdAt}</span>
                        <button
                            onClick={() => onCancel(intent.id)}
                            style={{
                                background: 'none', border: '1.5px solid #E7E5E4',
                                borderRadius: '10px', padding: '6px 12px',
                                fontSize: '12px', fontWeight: 600, color: '#A8A29E', cursor: 'pointer',
                            }}
                        >
                            Cancel
                        </button>
                    </div>
                </div>
            ))}
        </div>
    );
}

function ProposalTab({
    intents,
    onDecline,
    onAccept,
}: {
    intents: WalkIntent[];
    onDecline: (intentId: number, proposalId: number) => void;
    onAccept: (intentId: number, proposal: Proposal) => void;
}) {
    const totalProposals = intents.reduce((acc, i) => acc + i.proposals.length, 0);

    if (totalProposals === 0) {
        return (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', paddingTop: '52px' }}>
                <div
                    style={{
                        width: '80px', height: '80px', borderRadius: '24px',
                        background: 'linear-gradient(135deg, #FFF7ED, #FFEDD5)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        marginBottom: '16px', boxShadow: '0 8px 24px rgba(249,115,22,0.12)',
                    }}
                >
                    <Users size={32} strokeWidth={1.6} style={{ color: '#F97316' }} />
                </div>
                <p style={{ fontSize: '17px', fontWeight: 700, color: '#1C1917', marginBottom: '6px' }}>No proposals yet</p>
                <p style={{ fontSize: '14px', color: '#A8A29E', textAlign: 'center', lineHeight: 1.5 }}>
                    When someone wants to walk with you, their proposal will appear here.
                </p>
            </div>
        );
    }

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            {intents.map((intent) =>
                intent.proposals.length === 0 ? null : (
                    <div key={intent.id}>
                        {/* Intent header */}
                        <div
                            style={{
                                display: 'flex', alignItems: 'center', gap: '8px',
                                marginBottom: '10px', paddingLeft: '4px',
                            }}
                        >
                            <MapPin size={13} style={{ color: '#F97316' }} />
                            <span style={{ fontSize: '13px', fontWeight: 700, color: '#1C1917' }}>{intent.location}</span>
                            <span style={{ fontSize: '12px', color: '#A8A29E' }}>· {intent.date}</span>
                            <div
                                style={{
                                    marginLeft: 'auto', background: '#FFF7ED', borderRadius: '20px',
                                    padding: '3px 9px',
                                }}
                            >
                                <span style={{ fontSize: '11px', fontWeight: 700, color: '#F97316' }}>
                                    ⚡ {intent.proposals.length} new
                                </span>
                            </div>
                        </div>

                        {/* Proposal cards */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                            {intent.proposals.map((proposal) => (
                                <div
                                    key={proposal.id}
                                    style={{
                                        background: 'white', borderRadius: '20px', padding: '16px',
                                        border: '1.5px solid #F3F2F0', boxShadow: '0 4px 20px rgba(0,0,0,0.07)',
                                    }}
                                >
                                    {/* Person row */}
                                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px', marginBottom: '12px' }}>
                                        <div
                                            style={{
                                                width: '48px', height: '48px', borderRadius: '15px',
                                                background: proposal.avatarColor,
                                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                flexShrink: 0, boxShadow: `0 4px 12px ${proposal.avatarColor}55`,
                                            }}
                                        >
                                            <span style={{ fontSize: '16px', fontWeight: 700, color: 'white' }}>{proposal.initials}</span>
                                        </div>

                                        <div style={{ flex: 1, minWidth: 0 }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '7px', flexWrap: 'wrap' }}>
                                                <span style={{ fontSize: '15px', fontWeight: 800, color: '#1C1917' }}>{proposal.name}</span>
                                                <span style={{ fontSize: '12px', color: '#A8A29E' }}>{proposal.age} yrs</span>
                                            </div>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '4px', marginTop: '2px' }}>
                                                <MapPin size={10} style={{ color: '#A8A29E' }} />
                                                <span style={{ fontSize: '11px', color: '#A8A29E' }}>{proposal.distanceAway}</span>
                                            </div>
                                            <p style={{ fontSize: '13px', color: '#78716C', marginTop: '4px', lineHeight: 1.45 }}>{proposal.bio}</p>
                                        </div>

                                        {/* Compatibility badge */}
                                        <div
                                            style={{
                                                background: 'linear-gradient(135deg, #F97316, #FB923C)',
                                                borderRadius: '12px', padding: '6px 9px', flexShrink: 0,
                                                boxShadow: '0 4px 12px rgba(249,115,22,0.35)', textAlign: 'center',
                                            }}
                                        >
                                            <div style={{ fontSize: '14px', fontWeight: 800, color: 'white', lineHeight: 1 }}>
                                                {proposal.compatibility}%
                                            </div>
                                            <div style={{ fontSize: '9px', fontWeight: 600, color: 'rgba(255,255,255,0.8)', textTransform: 'uppercase' }}>
                                                Match
                                            </div>
                                        </div>
                                    </div>

                                    {/* Time / location strip */}
                                    <div
                                        style={{
                                            background: '#F9F8F7', borderRadius: '12px', padding: '10px 12px',
                                            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                            marginBottom: '12px',
                                        }}
                                    >
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                            <Clock size={12} style={{ color: '#A8A29E' }} />
                                            <span style={{ fontSize: '12px', fontWeight: 600, color: '#44403C' }}>{proposal.timeWindow}</span>
                                        </div>
                                        <div style={{ width: '1px', height: '12px', background: '#E7E5E4' }} />
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                            <MapPin size={12} style={{ color: '#A8A29E' }} />
                                            <span style={{ fontSize: '12px', fontWeight: 600, color: '#44403C' }}>{proposal.location}</span>
                                        </div>
                                    </div>

                                    {/* Action buttons */}
                                    <div style={{ display: 'flex', gap: '9px' }}>
                                        <button
                                            onClick={() => onDecline(intent.id, proposal.id)}
                                            style={{
                                                flex: 1, padding: '12px',
                                                background: 'white', border: '1.5px solid #E7E5E4',
                                                borderRadius: '13px', fontSize: '13px', fontWeight: 700, color: '#78716C',
                                                cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '5px',
                                            }}
                                        >
                                            <X size={14} strokeWidth={2.5} />
                                            Decline
                                        </button>
                                        <button
                                            onClick={() => onAccept(intent.id, proposal)}
                                            style={{
                                                flex: 2, padding: '12px',
                                                background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
                                                border: 'none', borderRadius: '13px',
                                                fontSize: '13px', fontWeight: 700, color: 'white',
                                                cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '5px',
                                                boxShadow: '0 6px 16px rgba(249,115,22,0.4)',
                                            }}
                                        >
                                            <Check size={15} strokeWidth={2.5} />
                                            Accept Walk
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                ),
            )}
        </div>
    );
}

function SessionTab({
    sessions,
    onStartWalk,
}: {
    sessions: Session[];
    onStartWalk: (session: Session) => void;
}) {
    if (sessions.length === 0) {
        return (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', paddingTop: '52px' }}>
                <div
                    style={{
                        width: '80px', height: '80px', borderRadius: '24px',
                        background: 'linear-gradient(135deg, #F0FDF4, #DCFCE7)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        marginBottom: '16px', boxShadow: '0 8px 24px rgba(22,163,74,0.12)',
                    }}
                >
                    <Zap size={32} strokeWidth={1.6} style={{ color: '#16A34A' }} />
                </div>
                <p style={{ fontSize: '17px', fontWeight: 700, color: '#1C1917', marginBottom: '6px' }}>No sessions yet</p>
                <p style={{ fontSize: '14px', color: '#A8A29E', textAlign: 'center', lineHeight: 1.5 }}>
                    Matched walks will appear here once you accept a proposal.
                </p>
            </div>
        );
    }

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {sessions.map((session) => (
                <div
                    key={session.id}
                    style={{
                        background: 'white', borderRadius: '20px', padding: '18px',
                        border: '1.5px solid #BBF7D0', boxShadow: '0 6px 22px rgba(22,163,74,0.08)',
                    }}
                >
                    {/* Match celebration row */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '14px' }}>
                        <div
                            style={{
                                width: '52px', height: '52px', borderRadius: '16px',
                                background: session.partnerColor,
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                flexShrink: 0, boxShadow: `0 6px 16px ${session.partnerColor}55`,
                            }}
                        >
                            <span style={{ fontSize: '18px', fontWeight: 700, color: 'white' }}>{session.partnerInitials}</span>
                        </div>

                        <div style={{ flex: 1 }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '7px', marginBottom: '3px' }}>
                                <span style={{ fontSize: '15px', fontWeight: 800, color: '#1C1917' }}>{session.partnerName}</span>
                                <span style={{ fontSize: '12px', color: '#A8A29E' }}>{session.partnerAge} yrs</span>
                            </div>
                            {/* Stars */}
                            <div style={{ display: 'flex', alignItems: 'center', gap: '2px' }}>
                                {[1, 2, 3, 4, 5].map((s) => (
                                    <Star
                                        key={s}
                                        size={11}
                                        fill={s <= session.rating ? '#F97316' : '#E7E5E4'}
                                        style={{ color: s <= session.rating ? '#F97316' : '#E7E5E4' }}
                                    />
                                ))}
                                <span style={{ fontSize: '11px', color: '#A8A29E', marginLeft: '3px' }}>{session.rating}.0</span>
                            </div>
                        </div>

                        {/* Compatibility */}
                        <div style={{ background: '#F0FDF4', borderRadius: '12px', padding: '7px 11px', textAlign: 'center' }}>
                            <div style={{ fontSize: '16px', fontWeight: 800, color: '#16A34A', lineHeight: 1 }}>{session.compatibility}%</div>
                            <div style={{ fontSize: '9px', fontWeight: 600, color: '#16A34A', marginTop: '1px' }}>Match</div>
                        </div>
                    </div>

                    {/* Walk details */}
                    <div
                        style={{
                            background: '#F9F8F7', borderRadius: '13px', padding: '11px 13px',
                            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                            marginBottom: '13px',
                        }}
                    >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                            <MapPin size={13} style={{ color: '#A8A29E' }} />
                            <span style={{ fontSize: '13px', fontWeight: 600, color: '#44403C' }}>{session.location}</span>
                        </div>
                        <div style={{ width: '1px', height: '14px', background: '#E7E5E4' }} />
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                            <Clock size={13} style={{ color: '#A8A29E' }} />
                            <span style={{ fontSize: '13px', fontWeight: 600, color: '#44403C' }}>{session.timeWindow}</span>
                        </div>
                    </div>

                    {/* Matched badge + CTA */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <div
                            style={{
                                display: 'flex', alignItems: 'center', gap: '5px',
                                background: '#F0FDF4', borderRadius: '20px', padding: '6px 11px', flexShrink: 0,
                            }}
                        >
                            <Sparkles size={12} style={{ color: '#16A34A' }} />
                            <span style={{ fontSize: '12px', fontWeight: 700, color: '#16A34A' }}>Matched ✓</span>
                        </div>

                        <button
                            onClick={() => onStartWalk(session)}
                            style={{
                                flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px',
                                background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
                                border: 'none', borderRadius: '14px', padding: '13px',
                                fontSize: '14px', fontWeight: 700, color: 'white', cursor: 'pointer',
                                boxShadow: '0 6px 18px rgba(249,115,22,0.4)',
                            }}
                        >
                            <Zap size={15} strokeWidth={2.5} />
                            Start Walk
                        </button>
                    </div>
                </div>
            ))}
        </div>
    );
}

// ─── Accept Confirm Sheet ─────────────────────────────────────────────────────

function AcceptSheet({
    proposal,
    onCancel,
    onConfirm,
}: {
    proposal: Proposal;
    onCancel: () => void;
    onConfirm: () => void;
}) {
    return (
        <div
            style={{
                position: 'fixed', inset: 0, zIndex: 60,
                background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)',
                display: 'flex', alignItems: 'flex-end',
            }}
            onClick={onCancel}
        >
            <div
                style={{
                    width: '100%', background: 'white',
                    borderRadius: '28px 28px 0 0', padding: '20px 24px 40px',
                    boxShadow: '0 -8px 32px rgba(0,0,0,0.15)',
                }}
                onClick={(e) => e.stopPropagation()}
            >
                <div style={{ width: '40px', height: '4px', background: '#E7E5E4', borderRadius: '2px', margin: '0 auto 20px' }} />

                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
                    <div
                        style={{
                            width: '52px', height: '52px', borderRadius: '16px',
                            background: proposal.avatarColor,
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                        }}
                    >
                        <span style={{ fontSize: '18px', fontWeight: 700, color: 'white' }}>{proposal.initials}</span>
                    </div>
                    <div>
                        <div style={{ fontSize: '17px', fontWeight: 800, color: '#1C1917' }}>{proposal.name}</div>
                        <div style={{ fontSize: '13px', color: '#A8A29E' }}>{proposal.location} · {proposal.timeWindow}</div>
                    </div>
                </div>

                <p style={{ fontSize: '15px', color: '#44403C', lineHeight: 1.6, marginBottom: '22px' }}>
                    Ready to accept? You'll both meet at <strong>{proposal.location}</strong> for your walk.
                </p>

                <div style={{ display: 'flex', gap: '12px' }}>
                    <button
                        onClick={onCancel}
                        style={{
                            flex: 1, padding: '15px', background: '#F5F5F4',
                            border: 'none', borderRadius: '14px',
                            fontSize: '15px', fontWeight: 700, color: '#78716C', cursor: 'pointer',
                        }}
                    >
                        Cancel
                    </button>
                    <button
                        onClick={onConfirm}
                        style={{
                            flex: 2, padding: '15px',
                            background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
                            border: 'none', borderRadius: '14px',
                            fontSize: '15px', fontWeight: 700, color: 'white', cursor: 'pointer',
                            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                            boxShadow: '0 6px 20px rgba(249,115,22,0.4)',
                        }}
                    >
                        <Check size={18} strokeWidth={2.5} />
                        Confirm Accept
                    </button>
                </div>
            </div>
        </div>
    );
}

// ─── Main Screen ──────────────────────────────────────────────────────────────

export function InvitationManagementScreen() {
    const navigate = useNavigate();

    const [activeTab, setActiveTab] = useState<SubTab>('finding');
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [findingIntents, setFindingIntents] = useState<WalkIntent[]>(FINDING_INTENTS);
    const [proposalIntents, setProposalIntents] = useState<WalkIntent[]>(PROPOSAL_INTENTS);
    const [sessions, setSessions] = useState<Session[]>(SESSIONS);
    const [acceptSheet, setAcceptSheet] = useState<{ intentId: number; proposal: Proposal } | null>(null);

    const handleRefresh = () => {
        setIsRefreshing(true);
        setTimeout(() => setIsRefreshing(false), 1400);
    };

    const handleCancelIntent = (id: number) => {
        setFindingIntents((prev) => prev.filter((i) => i.id !== id));
    };

    const handleDeclineProposal = (intentId: number, proposalId: number) => {
        setProposalIntents((prev) =>
            prev.map((i) =>
                i.id === intentId ? { ...i, proposals: i.proposals.filter((p) => p.id !== proposalId) } : i,
            ),
        );
    };

    const handleAcceptProposal = (intentId: number, proposal: Proposal) => {
        setAcceptSheet({ intentId, proposal });
    };

    const handleConfirmAccept = () => {
        if (!acceptSheet) return;
        const { intentId, proposal } = acceptSheet;

        // Move intent out of proposals, create a new session
        setProposalIntents((prev) =>
            prev.map((i) =>
                i.id === intentId ? { ...i, proposals: i.proposals.filter((p) => p.id !== proposal.id) } : i,
            ),
        );

        const intent = proposalIntents.find((i) => i.id === intentId);
        const newSession: Session = {
            id: Date.now(),
            partnerName: proposal.name,
            partnerInitials: proposal.initials,
            partnerColor: proposal.avatarColor,
            partnerAge: proposal.age,
            compatibility: proposal.compatibility,
            location: intent?.location ?? proposal.location,
            date: intent?.date ?? '',
            timeWindow: proposal.timeWindow,
            rating: 5,
        };

        setSessions((prev) => [newSession, ...prev]);
        setAcceptSheet(null);
        setActiveTab('session');
    };

    const handleStartWalk = (session: Session) => {
        navigate('/walk-session', {
            state: {
                partner: {
                    name: session.partnerName,
                    initials: session.partnerInitials,
                    avatarColor: session.partnerColor,
                    location: session.location,
                },
            },
        });
    };

    // Badge counts
    const proposalCount = proposalIntents.reduce((acc, i) => acc + i.proposals.length, 0);
    const sessionCount = sessions.length;

    // Tab config
    const TABS: { key: SubTab; label: string; badge?: number }[] = [
        { key: 'finding', label: 'Finding', badge: findingIntents.length },
        { key: 'proposal', label: 'Proposals', badge: proposalCount },
        { key: 'session', label: 'Sessions', badge: sessionCount },
    ];

    return (
        <div className="w-full h-full flex flex-col" style={{ background: '#FEF9F5' }}>
            {/* ── Header ─────────────────────────────────────────────── */}
            <div
                style={{
                    background: 'white', borderBottom: '1px solid #F3F2F0',
                    position: 'sticky', top: 0, zIndex: 20,
                    boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
                    padding: '52px 20px 0',
                }}
            >
                {/* Title row */}
                <div
                    style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        marginBottom: '14px',
                    }}
                >
                    <div>
                        <h1 style={{ fontSize: '22px', fontWeight: 800, color: '#1C1917', letterSpacing: '-0.4px' }}>
                            Matches
                        </h1>
                        <p style={{ fontSize: '12px', color: '#A8A29E', marginTop: '1px' }}>
                            {proposalCount > 0
                                ? `${proposalCount} proposal${proposalCount !== 1 ? 's' : ''} waiting for you`
                                : 'Your walks, proposals & sessions'}
                        </p>
                    </div>

                    <div style={{ display: 'flex', gap: '8px' }}>
                        <button
                            onClick={handleRefresh}
                            style={{
                                width: '40px', height: '40px', background: '#F5F5F4',
                                borderRadius: '12px', border: 'none', cursor: 'pointer',
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                            }}
                            aria-label="Refresh"
                        >
                            <RefreshCw
                                size={17} strokeWidth={2}
                                className={isRefreshing ? 'animate-spin' : ''}
                                style={{ color: '#44403C' }}
                            />
                        </button>

                        <button
                            onClick={() => navigate('/find')}
                            style={{
                                height: '40px', padding: '0 14px',
                                background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
                                borderRadius: '12px', border: 'none', cursor: 'pointer',
                                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '5px',
                                boxShadow: '0 4px 12px rgba(249,115,22,0.35)',
                            }}
                        >
                            <Plus size={15} color="white" strokeWidth={2.5} />
                            <span style={{ fontSize: '13px', fontWeight: 700, color: 'white' }}>New</span>
                        </button>
                    </div>
                </div>

                {/* ── Big Sub-Tabs ────────────────────────────────────── */}
                <div style={{ display: 'flex', gap: '0', borderTop: '1px solid #F3F2F0' }}>
                    {TABS.map((tab) => {
                        const isActive = activeTab === tab.key;
                        return (
                            <button
                                key={tab.key}
                                onClick={() => setActiveTab(tab.key)}
                                style={{
                                    flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px',
                                    padding: '13px 6px',
                                    background: 'none', border: 'none', cursor: 'pointer',
                                    borderBottom: isActive ? '2.5px solid #F97316' : '2.5px solid transparent',
                                    marginBottom: '-1px',
                                    transition: 'border-color 0.2s',
                                }}
                            >
                                <span
                                    style={{
                                        fontSize: '13px',
                                        fontWeight: isActive ? 800 : 600,
                                        color: isActive ? '#F97316' : '#A8A29E',
                                        transition: 'color 0.2s',
                                    }}
                                >
                                    {tab.label}
                                </span>
                                {tab.badge !== undefined && tab.badge > 0 && (
                                    <div
                                        style={{
                                            minWidth: '18px', height: '18px', borderRadius: '9px', padding: '0 5px',
                                            background: isActive ? '#F97316' : '#E7E5E4',
                                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            transition: 'background 0.2s',
                                        }}
                                    >
                                        <span
                                            style={{
                                                fontSize: '10px', fontWeight: 800,
                                                color: isActive ? 'white' : '#78716C',
                                            }}
                                        >
                                            {tab.badge}
                                        </span>
                                    </div>
                                )}
                            </button>
                        );
                    })}
                </div>
            </div>

            {/* ── Tab Content ────────────────────────────────────────── */}
            <div
                className="flex-1 overflow-y-auto mobile-scroll"
                style={{ padding: '16px 16px 90px' }}
            >
                {activeTab === 'finding' && (
                    <FindingTab
                        intents={findingIntents}
                        onCancel={handleCancelIntent}
                        onNew={() => navigate('/find')}
                    />
                )}
                {activeTab === 'proposal' && (
                    <ProposalTab
                        intents={proposalIntents}
                        onDecline={handleDeclineProposal}
                        onAccept={handleAcceptProposal}
                    />
                )}
                {activeTab === 'session' && (
                    <SessionTab
                        sessions={sessions}
                        onStartWalk={handleStartWalk}
                    />
                )}
            </div>

            {/* Accept confirmation sheet */}
            {acceptSheet && (
                <AcceptSheet
                    proposal={acceptSheet.proposal}
                    onCancel={() => setAcceptSheet(null)}
                    onConfirm={handleConfirmAccept}
                />
            )}

            <BottomNav />
        </div>
    );
}
