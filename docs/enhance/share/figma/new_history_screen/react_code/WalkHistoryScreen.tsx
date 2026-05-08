import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ChevronLeft, MapPin, Clock, MessageCircle, FileText, AlertTriangle } from 'lucide-react';
import { BottomNav } from '../layout/BottomNav';

type SessionStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

interface WalkSession {
    id: number;
    date: string;
    status: SessionStatus;
    partner: { name: string; initials: string; color: string };
    partnerStatus: string;
    yourStatus: string;
    partnerKm: string;
    partnerMin: string;
    yourKm: string;
    yourMin: string;
    location?: string;
    // Initial mock state
    initReviewed?: boolean;
    initPosted?: boolean;
}

const SESSIONS: WalkSession[] = [
    {
        id: 1,
        date: '2026-04-29',
        status: 'ACTIVE',
        partner: { name: 'Bảo Duy Nguyễn', initials: 'BD', color: '#7C3AED' },
        partnerStatus: 'Waiting...',
        yourStatus: 'Completed',
        partnerKm: '0,00 km', partnerMin: '0 min',
        yourKm: '0,01 km', yourMin: '1 min',
        location: 'Le Van Tam Park',
        initReviewed: false, initPosted: false,
    },
    {
        id: 2,
        date: '2026-04-29',
        status: 'COMPLETED',
        partner: { name: 'Bảo Duy Nguyễn', initials: 'BD', color: '#7C3AED' },
        partnerStatus: 'Completed',
        yourStatus: 'Completed',
        partnerKm: '0,01 km', partnerMin: '1 min',
        yourKm: '0,01 km', yourMin: '0 min',
        location: 'Le Van Tam Park',
        initReviewed: false, initPosted: false,  // State A
    },
    {
        id: 3,
        date: '2026-04-25',
        status: 'COMPLETED',
        partner: { name: 'Bảo Duy Nguyễn', initials: 'BD', color: '#7C3AED' },
        partnerStatus: 'Completed',
        yourStatus: 'Completed',
        partnerKm: '0,00 km', partnerMin: '0 min',
        yourKm: '0,00 km', yourMin: '0 min',
        location: 'Tao Dan Park',
        initReviewed: true, initPosted: false,   // State B
    },
    {
        id: 4,
        date: '2026-04-22',
        status: 'COMPLETED',
        partner: { name: 'Tran Linh', initials: 'TL', color: '#0EA5E9' },
        partnerStatus: 'Completed',
        yourStatus: 'Completed',
        partnerKm: '1,20 km', partnerMin: '18 min',
        yourKm: '1,20 km', yourMin: '18 min',
        location: 'Binh Quoi Park',
        initReviewed: true, initPosted: true,    // State D
    },
    {
        id: 5,
        date: '2026-04-18',
        status: 'CANCELLED',
        partner: { name: 'Mai Nguyen', initials: 'MN', color: '#EF4444' },
        partnerStatus: 'Cancelled',
        yourStatus: 'Cancelled',
        partnerKm: '0,00 km', partnerMin: '0 min',
        yourKm: '0,00 km', yourMin: '0 min',
        location: 'Gia Dinh Park',
        initReviewed: false, initPosted: false,  // State F
    },
];

const STATUS_STYLES: Record<
    SessionStatus,
    { bg: string; color: string; label: string }
> = {
    ACTIVE: { bg: '#FFF7ED', color: '#F97316', label: 'ACTIVE' },
    COMPLETED: { bg: '#F0FDF4', color: '#16A34A', label: 'COMPLETED' },
    CANCELLED: { bg: '#FEF2F2', color: '#EF4444', label: 'CANCELLED' },
    NO_SHOW: { bg: '#FFF7ED', color: '#EA580C', label: 'NO-SHOW' },
};

interface SessionState { reviewed: boolean; posted: boolean }

function PostedChip() {
    return (
        <div
            style={{
                display: 'inline-flex', alignItems: 'center', gap: 4,
                background: '#F5F3FF', border: '1.5px solid #DDD6FE',
                borderRadius: 100, padding: '2px 8px',
            }}
        >
            <span style={{ fontSize: 10, fontWeight: 700, color: '#7C3AED', letterSpacing: '0.3px' }}>
                POSTED
            </span>
        </div>
    );
}

export function WalkHistoryScreen() {
    const navigate = useNavigate();

    // Per-session interactive state
    const [states, setStates] = useState<Record<number, SessionState>>(() => {
        const init: Record<number, SessionState> = {};
        for (const s of SESSIONS) {
            init[s.id] = { reviewed: s.initReviewed ?? false, posted: s.initPosted ?? false };
        }
        return init;
    });

    function markReviewed(id: number) {
        setStates((prev) => ({ ...prev, [id]: { ...prev[id], reviewed: true } }));
    }

    return (
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
                    Walk History
                </h2>
                <div style={{ width: 40 }} />
            </div>

            {/* List */}
            <div className="flex-1 overflow-y-auto mobile-scroll" style={{ padding: '16px 16px 88px' }}>
                <div className="flex flex-col gap-3">
                    {SESSIONS.map((session) => {
                        const st = states[session.id];
                        const statusStyle = STATUS_STYLES[session.status];
                        const isCompleted = session.status === 'COMPLETED';
                        const isCancelled = session.status === 'CANCELLED' || session.status === 'NO_SHOW';
                        const isActiveYoursDone = session.status === 'ACTIVE' && session.yourStatus === 'Completed';

                        return (
                            <div
                                key={session.id}
                                style={{
                                    background: 'white', borderRadius: 20,
                                    border: '1.5px solid #F3F2F0',
                                    boxShadow: '0 4px 16px rgba(0,0,0,0.06)',
                                    overflow: 'hidden',
                                }}
                            >
                                {/* ── Top bar ── */}
                                <div
                                    className="flex items-center justify-between"
                                    style={{ padding: '14px 16px 10px', gap: 8 }}
                                >
                                    <span style={{ fontSize: 14, fontWeight: 700, color: '#1C1917' }}>
                                        {session.date}
                                    </span>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                        {/* POSTED chip for completed + posted sessions */}
                                        {(isCompleted || isActiveYoursDone) && st.posted && <PostedChip />}
                                        <div
                                            style={{ background: statusStyle.bg, borderRadius: 100, padding: '3px 10px' }}
                                        >
                                            <span style={{ fontSize: 11, fontWeight: 700, color: statusStyle.color, letterSpacing: '0.5px' }}>
                                                {statusStyle.label}
                                            </span>
                                        </div>
                                    </div>
                                </div>

                                {/* Location */}
                                {session.location && (
                                    <div
                                        style={{
                                            display: 'flex', alignItems: 'center', gap: 4,
                                            padding: '0 16px 8px',
                                        }}
                                    >
                                        <MapPin size={11} strokeWidth={2} style={{ color: '#A8A29E' }} />
                                        <span style={{ fontSize: 12, color: '#78716C', fontWeight: 600 }}>
                                            {session.location}
                                        </span>
                                    </div>
                                )}

                                <div style={{ height: 1, background: '#F3F2F0', margin: '0 16px' }} />

                                {/* Partner row */}
                                <ParticipantRow
                                    name={session.partner.name}
                                    initials={session.partner.initials}
                                    color={session.partner.color}
                                    status={session.partnerStatus}
                                    km={session.partnerKm}
                                    min={session.partnerMin}
                                    pt="12px" pb="4px"
                                />

                                {/* You row */}
                                <ParticipantRow
                                    name="You"
                                    initials="LT"
                                    color="#F97316"
                                    status={session.yourStatus}
                                    km={session.yourKm}
                                    min={session.yourMin}
                                    pt="4px" pb="12px"
                                />

                                {/* ── Action area ── */}
                                <div style={{ height: 1, background: '#F3F2F0', margin: '0 16px' }} />

                                {/* ─── State F: Cancelled / No-show ─── */}
                                {isCancelled && (
                                    <div
                                        style={{
                                            display: 'flex', gap: 8,
                                            padding: '10px 16px 12px',
                                        }}
                                    >
                                        <button
                                            onClick={() => navigate('/chat', { state: { partner: session.partner, sessionId: session.id } })}
                                            style={secondaryBtnStyle}
                                        >
                                            <FileText size={13} strokeWidth={2.5} />
                                            View Details
                                        </button>
                                        <button style={reportBtnStyle}>
                                            <AlertTriangle size={13} strokeWidth={2.5} />
                                            Report
                                        </button>
                                    </div>
                                )}

                                {/* ─── State E: Your walk done, partner still waiting ─── */}
                                {isActiveYoursDone && (
                                    <div style={{ padding: '10px 16px 12px', display: 'flex', flexDirection: 'column', gap: 7 }}>
                                        <button
                                            onClick={() =>
                                                navigate('/walk-post/create', {
                                                    state: { sessionId: session.id, partnerName: session.partner.name, myWalkOnly: true },
                                                })
                                            }
                                            style={outlineBtnStyle}
                                        >
                                            Post My Walk
                                        </button>
                                        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                                            <button style={ghostReportStyle}>
                                                <AlertTriangle size={11} strokeWidth={2.5} />
                                                Report
                                            </button>
                                        </div>
                                    </div>
                                )}

                                {/* ─── States A / B / C / D: Completed ─── */}
                                {isCompleted && (
                                    <div style={{ padding: '10px 16px 12px', display: 'flex', flexDirection: 'column', gap: 7 }}>
                                        {/* Main row: Review + Post */}
                                        <div style={{ display: 'flex', gap: 8 }}>
                                            {/* Review button */}
                                            {!st.reviewed ? (
                                                <button
                                                    onClick={() => {
                                                        navigate('/leave-review', { state: { partner: session.partner, sessionId: session.id } });
                                                        markReviewed(session.id);
                                                    }}
                                                    style={primaryBtnStyle}
                                                >
                                                    ★ Leave a Review
                                                </button>
                                            ) : (
                                                <button
                                                    onClick={() =>
                                                        navigate('/leave-review', { state: { partner: session.partner, sessionId: session.id, viewMode: true } })
                                                    }
                                                    style={reviewedBtnStyle}
                                                >
                                                    ✓ View Review
                                                </button>
                                            )}

                                            {/* Post button */}
                                            {!st.posted ? (
                                                <button
                                                    onClick={() =>
                                                        navigate('/walk-post/create', {
                                                            state: { sessionId: session.id, partnerName: session.partner.name },
                                                        })
                                                    }
                                                    style={outlineBtnStyle}
                                                >
                                                    Post to Profile
                                                </button>
                                            ) : (
                                                <button
                                                    onClick={() => navigate('/walk-activity')}
                                                    style={postedBtnStyle}
                                                >
                                                    View Post
                                                </button>
                                            )}
                                        </div>

                                        {/* Tertiary: Chat + Report */}
                                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                            <button
                                                onClick={() =>
                                                    navigate('/chat', { state: { partner: session.partner, sessionId: session.id, location: session.location } })
                                                }
                                                style={chatBtnStyle}
                                            >
                                                <MessageCircle size={12} strokeWidth={2.5} />
                                                Chat
                                            </button>
                                            <div style={{ flex: 1 }} />
                                            <button style={ghostReportStyle}>
                                                <AlertTriangle size={11} strokeWidth={2.5} />
                                                Report
                                            </button>
                                        </div>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>

            <BottomNav />
        </div>
    );
}

/* ── Sub-components ── */

function ParticipantRow({
    name, initials, color, status, km, min, pt, pb,
}: {
    name: string; initials: string; color: string; status: string;
    km: string; min: string; pt: string; pb: string;
}) {
    return (
        <div className="flex items-center" style={{ padding: `${pt} 16px ${pb}` }}>
            <div className="flex items-center gap-2.5 flex-1">
                <div
                    style={{
                        width: 36, height: 36, borderRadius: '50%',
                        background: color,
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        flexShrink: 0,
                        boxShadow: `0 3px 8px ${color}44`,
                    }}
                >
                    <span style={{ fontSize: 12, fontWeight: 700, color: 'white' }}>{initials}</span>
                </div>
                <div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: '#1C1917' }}>{name}</div>
                    <div style={{ fontSize: 12, color: '#A8A29E', fontWeight: 500 }}>{status}</div>
                </div>
            </div>
            <div className="flex items-center gap-3">
                <div className="flex items-center gap-1">
                    <MapPin size={11} style={{ color: '#A8A29E' }} />
                    <span style={{ fontSize: 12, color: '#78716C', fontWeight: 600 }}>{km}</span>
                </div>
                <div className="flex items-center gap-1">
                    <Clock size={11} style={{ color: '#A8A29E' }} />
                    <span style={{ fontSize: 12, color: '#78716C', fontWeight: 600 }}>{min}</span>
                </div>
            </div>
        </div>
    );
}

/* ── Button style objects ── */

const primaryBtnStyle: React.CSSProperties = {
    flex: 1,
    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5,
    padding: '10px 12px',
    background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
    border: 'none', borderRadius: 12,
    cursor: 'pointer', fontSize: 13, fontWeight: 700, color: 'white',
    boxShadow: '0 4px 12px rgba(249,115,22,0.30)',
};

const reviewedBtnStyle: React.CSSProperties = {
    flex: 1,
    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5,
    padding: '10px 12px',
    background: '#F0FDF4',
    border: '1.5px solid #86EFAC',
    borderRadius: 12,
    cursor: 'pointer', fontSize: 13, fontWeight: 700, color: '#16A34A',
};

const outlineBtnStyle: React.CSSProperties = {
    flex: 1,
    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5,
    padding: '10px 12px',
    background: '#FFF7ED',
    border: '1.5px solid #FED7AA',
    borderRadius: 12,
    cursor: 'pointer', fontSize: 13, fontWeight: 700, color: '#F97316',
};

const postedBtnStyle: React.CSSProperties = {
    flex: 1,
    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5,
    padding: '10px 12px',
    background: '#F5F3FF',
    border: '1.5px solid #DDD6FE',
    borderRadius: 12,
    cursor: 'pointer', fontSize: 13, fontWeight: 700, color: '#7C3AED',
};

const secondaryBtnStyle: React.CSSProperties = {
    flex: 1,
    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5,
    padding: '9px 12px',
    background: '#F5F5F4',
    border: '1.5px solid #E7E5E4',
    borderRadius: 12,
    cursor: 'pointer', fontSize: 13, fontWeight: 700, color: '#44403C',
};

const reportBtnStyle: React.CSSProperties = {
    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5,
    padding: '9px 14px',
    background: 'transparent',
    border: '1.5px solid #FECACA',
    borderRadius: 12,
    cursor: 'pointer', fontSize: 13, fontWeight: 700, color: '#EF4444',
};

const chatBtnStyle: React.CSSProperties = {
    display: 'flex', alignItems: 'center', gap: 4,
    padding: '6px 11px',
    background: '#FFF7ED',
    border: '1.5px solid #FED7AA',
    borderRadius: 10,
    cursor: 'pointer', fontSize: 12, fontWeight: 600, color: '#F97316',
};

const ghostReportStyle: React.CSSProperties = {
    display: 'flex', alignItems: 'center', gap: 4,
    background: 'none', border: 'none',
    cursor: 'pointer', fontSize: 12, fontWeight: 600, color: '#A8A29E',
    padding: '4px 0',
};
