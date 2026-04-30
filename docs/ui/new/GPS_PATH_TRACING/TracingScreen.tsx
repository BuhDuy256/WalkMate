import { useEffect, useRef, useState } from 'react';
import { useNavigate, useLocation } from 'react-router';
import { MapPin, Clock, Footprints, Zap, Star, X, ChevronRight } from 'lucide-react';

const GPS_PATH: [number, number][] = [
    [10.7891, 106.6891],
    [10.7882, 106.6875],
    [10.7870, 106.6861],
    [10.7856, 106.6874],
    [10.7843, 106.6893],
    [10.7837, 106.6914],
    [10.7843, 106.6932],
    [10.7856, 106.6943],
    [10.7870, 106.6937],
    [10.7884, 106.6921],
];

const DEFAULT_PARTNER = {
    name: 'Nguyen Minh',
    initials: 'NM',
    avatarColor: '#7C3AED',
    location: 'Le Van Tam Park',
};

function formatTime(secs: number) {
    const m = Math.floor(secs / 60).toString().padStart(2, '0');
    const s = (secs % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
}

export function WalkSessionScreen() {
    const navigate = useNavigate();
    const location = useLocation();
    const mapRef = useRef<HTMLDivElement>(null);
    const leafletMapRef = useRef<any>(null);
    const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

    const partner = (location.state as any)?.partner ?? DEFAULT_PARTNER;

    const [elapsed, setElapsed] = useState(0);
    const [showCompleted, setShowCompleted] = useState(false);
    const [rating, setRating] = useState(0);

    // Simulated walk stats
    const distance = (0.12 + (elapsed / 60) * 0.08).toFixed(2);
    const steps = Math.floor(elapsed * 1.4);

    useEffect(() => {
        timerRef.current = setInterval(() => setElapsed((e) => e + 1), 1000);
        return () => { if (timerRef.current) clearInterval(timerRef.current); };
    }, []);

    useEffect(() => {
        let map: any;

        const initMap = async () => {
            try {
                const leaflet = await import('leaflet');
                const L = leaflet.default || leaflet;

                if (!mapRef.current || leafletMapRef.current) return;

                const currentPos = GPS_PATH[GPS_PATH.length - 1];

                map = L.map(mapRef.current, {
                    center: currentPos,
                    zoom: 16,
                    zoomControl: false,
                    attributionControl: false,
                });
                leafletMapRef.current = map;

                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    attribution: '© OpenStreetMap',
                }).addTo(map);

                // Draw GPS path
                const polyline = L.polyline(GPS_PATH, {
                    color: '#F97316',
                    weight: 5,
                    opacity: 0.92,
                    lineCap: 'round',
                    lineJoin: 'round',
                    dashArray: undefined,
                    smoothFactor: 1,
                }).addTo(map);

                // Start marker
                const startIcon = L.divIcon({
                    html: `<div style="
            width:14px; height:14px; border-radius:50%;
            background:#22C55E; border:3px solid white;
            box-shadow:0 2px 8px rgba(34,197,94,0.6);
          "></div>`,
                    className: '',
                    iconSize: [14, 14],
                    iconAnchor: [7, 7],
                });
                L.marker(GPS_PATH[0], { icon: startIcon }).addTo(map);

                // Current location pulsing marker
                const pulseIcon = L.divIcon({
                    html: `
            <div class="gps-pulse-container" style="width:32px;height:32px;position:relative;display:flex;align-items:center;justify-content:center;">
              <div class="gps-pulse-ring" style="width:32px;height:32px;position:absolute;border-radius:50%;background:rgba(249,115,22,0.25);animation:wm-gps-pulse 1.6s ease-out infinite;"></div>
              <div class="gps-pulse-ring" style="width:32px;height:32px;position:absolute;border-radius:50%;background:rgba(249,115,22,0.15);animation:wm-gps-pulse 1.6s ease-out 0.5s infinite;"></div>
              <div style="width:14px;height:14px;border-radius:50%;background:#F97316;border:2.5px solid white;box-shadow:0 2px 8px rgba(249,115,22,0.7);position:relative;z-index:2;"></div>
            </div>
          `,
                    className: '',
                    iconSize: [32, 32],
                    iconAnchor: [16, 16],
                });
                L.marker(currentPos, { icon: pulseIcon }).addTo(map);

                // Fit map to path
                map.fitBounds(polyline.getBounds(), { padding: [60, 60] });

                setTimeout(() => map.invalidateSize(), 100);
            } catch (e) {
                console.error('Map error:', e);
            }
        };

        initMap();
        return () => {
            if (leafletMapRef.current) {
                leafletMapRef.current.remove();
                leafletMapRef.current = null;
            }
        };
    }, []);

    const handleEndWalk = () => {
        if (timerRef.current) clearInterval(timerRef.current);
        setShowCompleted(true);
    };

    return (
        <div className="w-full h-full flex flex-col" style={{ background: '#111', position: 'relative' }}>

            {/* ── MAP ─────────────────────────────────────────────────────── */}
            <div className="flex-1 relative" style={{ minHeight: 0, overflow: 'hidden' }}>
                <div ref={mapRef} style={{ position: 'absolute', inset: 0 }} />

                {/* ── Top Stats Overlay ──────────────────────────────────────── */}
                <div
                    style={{
                        position: 'absolute', top: 0, left: 0, right: 0, zIndex: 20,
                        padding: '52px 16px 0',
                    }}
                >
                    <div
                        style={{
                            background: 'rgba(255,255,255,0.97)',
                            backdropFilter: 'blur(16px)',
                            borderRadius: '22px',
                            padding: '16px 20px',
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            boxShadow: '0 8px 32px rgba(0,0,0,0.16)',
                            border: '1.5px solid rgba(243,242,240,0.9)',
                        }}
                    >
                        {/* Timer */}
                        <div style={{ textAlign: 'center', flex: 1 }}>
                            <div
                                style={{
                                    fontSize: '24px', fontWeight: 800, color: '#1C1917',
                                    letterSpacing: '-0.6px', fontVariantNumeric: 'tabular-nums', lineHeight: 1,
                                }}
                            >
                                {formatTime(elapsed)}
                            </div>
                            <div
                                style={{
                                    fontSize: '10px', fontWeight: 700, color: '#A8A29E',
                                    letterSpacing: '0.8px', textTransform: 'uppercase', marginTop: '4px',
                                }}
                            >
                                Time
                            </div>
                        </div>

                        {/* Divider */}
                        <div style={{ width: '1px', height: '36px', background: '#F3F2F0', flexShrink: 0 }} />

                        {/* Distance */}
                        <div style={{ textAlign: 'center', flex: 1 }}>
                            <div
                                style={{
                                    fontSize: '24px', fontWeight: 800, color: '#F97316',
                                    letterSpacing: '-0.6px', lineHeight: 1,
                                }}
                            >
                                {distance}
                            </div>
                            <div
                                style={{
                                    fontSize: '10px', fontWeight: 700, color: '#A8A29E',
                                    letterSpacing: '0.8px', textTransform: 'uppercase', marginTop: '4px',
                                }}
                            >
                                KM
                            </div>
                        </div>

                        {/* Divider */}
                        <div style={{ width: '1px', height: '36px', background: '#F3F2F0', flexShrink: 0 }} />

                        {/* Steps */}
                        <div style={{ textAlign: 'center', flex: 1 }}>
                            <div
                                style={{
                                    fontSize: '24px', fontWeight: 800, color: '#1C1917',
                                    letterSpacing: '-0.6px', lineHeight: 1,
                                }}
                            >
                                {steps.toLocaleString()}
                            </div>
                            <div
                                style={{
                                    fontSize: '10px', fontWeight: 700, color: '#A8A29E',
                                    letterSpacing: '0.8px', textTransform: 'uppercase', marginTop: '4px',
                                }}
                            >
                                Steps
                            </div>
                        </div>
                    </div>
                </div>

                {/* ── Map Legend ─────────────────────────────────────────────── */}
                <div
                    style={{
                        position: 'absolute', bottom: '14px', right: '14px', zIndex: 20,
                        background: 'rgba(255,255,255,0.97)',
                        backdropFilter: 'blur(12px)',
                        borderRadius: '14px',
                        padding: '10px 14px',
                        boxShadow: '0 4px 16px rgba(0,0,0,0.14)',
                        border: '1px solid rgba(243,242,240,0.9)',
                        display: 'flex', flexDirection: 'column', gap: '6px',
                    }}
                >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '7px' }}>
                        <div style={{ width: '18px', height: '3px', background: '#F97316', borderRadius: '2px' }} />
                        <span style={{ fontSize: '11px', fontWeight: 600, color: '#44403C' }}>Your route</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '7px' }}>
                        <div
                            style={{
                                width: '10px', height: '10px', borderRadius: '50%',
                                background: '#F97316', border: '2px solid white',
                                boxShadow: '0 0 0 2px rgba(249,115,22,0.3)', flexShrink: 0,
                            }}
                        />
                        <span style={{ fontSize: '11px', fontWeight: 600, color: '#44403C' }}>Current</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '7px' }}>
                        <div
                            style={{
                                width: '10px', height: '10px', borderRadius: '50%',
                                background: '#22C55E', border: '2px solid white', flexShrink: 0,
                            }}
                        />
                        <span style={{ fontSize: '11px', fontWeight: 600, color: '#44403C' }}>Start</span>
                    </div>
                </div>
            </div>

            {/* ── Bottom Sheet ────────────────────────────────────────────── */}
            <div
                style={{
                    background: 'white',
                    borderRadius: '28px 28px 0 0',
                    padding: '10px 20px 36px',
                    boxShadow: '0 -10px 40px rgba(0,0,0,0.13)',
                    border: '1px solid #F3F2F0',
                    zIndex: 20,
                    flexShrink: 0,
                }}
            >
                {/* Drag handle */}
                <div
                    style={{
                        width: '36px', height: '4px', background: '#E7E5E4',
                        borderRadius: '2px', margin: '0 auto 16px',
                    }}
                />

                {/* Partner row */}
                <div
                    style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        marginBottom: '16px',
                        background: '#FAFAF9',
                        borderRadius: '18px',
                        padding: '12px 14px',
                        border: '1.5px solid #F3F2F0',
                    }}
                >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div
                            style={{
                                width: '46px', height: '46px', borderRadius: '14px',
                                background: partner.avatarColor,
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                flexShrink: 0,
                                boxShadow: `0 4px 14px ${partner.avatarColor}55`,
                            }}
                        >
                            <span style={{ fontSize: '16px', fontWeight: 700, color: 'white' }}>
                                {partner.initials}
                            </span>
                        </div>
                        <div>
                            <div style={{ fontSize: '14px', fontWeight: 700, color: '#1C1917', letterSpacing: '-0.1px' }}>
                                Walking with {partner.name}
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '4px', marginTop: '3px' }}>
                                <MapPin size={11} style={{ color: '#F97316' }} strokeWidth={2} />
                                <span style={{ fontSize: '12px', color: '#A8A29E', fontWeight: 500 }}>
                                    {partner.location}
                                </span>
                            </div>
                        </div>
                    </div>

                    {/* LIVE badge */}
                    <div
                        style={{
                            display: 'flex', alignItems: 'center', gap: '5px',
                            background: '#DCFCE7', borderRadius: '20px', padding: '6px 11px',
                            border: '1px solid #BBF7D0',
                            flexShrink: 0,
                        }}
                    >
                        <div
                            style={{
                                width: '7px', height: '7px', borderRadius: '50%',
                                background: '#22C55E',
                                boxShadow: '0 0 0 2px rgba(34,197,94,0.25)',
                            }}
                        />
                        <span style={{ fontSize: '12px', fontWeight: 800, color: '#16A34A', letterSpacing: '0.3px' }}>
                            LIVE
                        </span>
                    </div>
                </div>

                {/* End Walk button */}
                <button
                    onClick={handleEndWalk}
                    style={{
                        width: '100%',
                        display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '10px',
                        background: 'linear-gradient(135deg, #EF4444 0%, #F87171 100%)',
                        border: 'none', borderRadius: '18px', padding: '17px',
                        fontSize: '15px', fontWeight: 800, color: 'white',
                        cursor: 'pointer',
                        boxShadow: '0 8px 24px rgba(239,68,68,0.38)',
                        letterSpacing: '-0.1px',
                    }}
                >
                    <X size={18} strokeWidth={2.5} />
                    End Walk
                </button>
            </div>

            {/* ── Walk Completed Overlay ───────────────────────────────────── */}
            {showCompleted && (
                <div
                    style={{
                        position: 'absolute', inset: 0, zIndex: 100,
                        background: '#FEF9F5',
                        display: 'flex', flexDirection: 'column',
                        overflowY: 'auto',
                    }}
                >
                    {/* ── Hero ───────────────────────────────────────────────── */}
                    <div
                        style={{
                            padding: '56px 24px 28px',
                            display: 'flex', flexDirection: 'column', alignItems: 'center',
                        }}
                    >
                        {/* Celebration icon */}
                        <div
                            style={{
                                width: '84px', height: '84px', borderRadius: '28px',
                                background: 'linear-gradient(135deg, #F97316, #FB923C)',
                                boxShadow: '0 14px 40px rgba(249,115,22,0.48)',
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                marginBottom: '18px',
                                animation: 'wm-match-pop 0.5s ease-out',
                            }}
                        >
                            <Zap size={38} color="white" strokeWidth={1.8} />
                        </div>

                        <h1
                            style={{
                                fontSize: '28px', fontWeight: 800, color: '#1C1917',
                                letterSpacing: '-0.5px', textAlign: 'center', marginBottom: '6px',
                            }}
                        >
                            Walk Complete! 🎉
                        </h1>
                        <p style={{ fontSize: '14px', color: '#A8A29E', textAlign: 'center' }}>
                            Great walk with {partner.name}!
                        </p>
                    </div>

                    {/* ── Stats Card ─────────────────────────────────────────── */}
                    <div style={{ padding: '0 20px', marginBottom: '14px' }}>
                        <div
                            style={{
                                background: 'white',
                                borderRadius: '24px',
                                padding: '22px',
                                border: '1.5px solid #F3F2F0',
                                boxShadow: '0 6px 24px rgba(0,0,0,0.07)',
                            }}
                        >
                            {/* Top shimmer accent */}
                            <div
                                style={{
                                    height: '3px',
                                    background: 'linear-gradient(90deg, transparent, #F97316, transparent)',
                                    borderRadius: '2px',
                                    marginBottom: '20px',
                                    opacity: 0.7,
                                }}
                            />
                            <div
                                style={{
                                    display: 'grid', gridTemplateColumns: '1fr 1fr 1fr',
                                    gap: '4px', textAlign: 'center',
                                }}
                            >
                                {[
                                    { icon: Clock, label: 'Duration', value: formatTime(elapsed) },
                                    { icon: MapPin, label: 'Distance', value: `${distance} km` },
                                    { icon: Footprints, label: 'Steps', value: steps.toLocaleString() },
                                ].map((stat, i) => (
                                    <div
                                        key={stat.label}
                                        style={{
                                            padding: '4px',
                                            borderRight: i < 2 ? '1px solid #F3F2F0' : 'none',
                                        }}
                                    >
                                        <div
                                            style={{
                                                width: '40px', height: '40px', borderRadius: '13px',
                                                background: '#FFF7ED',
                                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                margin: '0 auto 10px',
                                                boxShadow: '0 2px 8px rgba(249,115,22,0.12)',
                                            }}
                                        >
                                            <stat.icon size={18} style={{ color: '#F97316' }} strokeWidth={2} />
                                        </div>
                                        <div
                                            style={{
                                                fontSize: '17px', fontWeight: 800, color: '#1C1917',
                                                lineHeight: 1, letterSpacing: '-0.3px',
                                            }}
                                        >
                                            {stat.value}
                                        </div>
                                        <div
                                            style={{
                                                fontSize: '11px', color: '#A8A29E', marginTop: '4px',
                                                fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px',
                                            }}
                                        >
                                            {stat.label}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>

                    {/* ── Rate Partner Card ─────────────────────────────────── */}
                    <div style={{ padding: '0 20px', marginBottom: '20px' }}>
                        <div
                            style={{
                                background: 'white',
                                borderRadius: '24px',
                                padding: '20px',
                                border: '1.5px solid #F3F2F0',
                                boxShadow: '0 6px 24px rgba(0,0,0,0.07)',
                            }}
                        >
                            {/* Partner row */}
                            <div
                                style={{
                                    display: 'flex', alignItems: 'center', gap: '13px',
                                    marginBottom: '18px',
                                }}
                            >
                                <div
                                    style={{
                                        width: '50px', height: '50px', borderRadius: '15px',
                                        background: partner.avatarColor,
                                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                                        flexShrink: 0,
                                        boxShadow: `0 5px 16px ${partner.avatarColor}55`,
                                    }}
                                >
                                    <span style={{ fontSize: '17px', fontWeight: 700, color: 'white' }}>
                                        {partner.initials}
                                    </span>
                                </div>
                                <div>
                                    <div style={{ fontSize: '15px', fontWeight: 800, color: '#1C1917', letterSpacing: '-0.2px' }}>
                                        Rate {partner.name}
                                    </div>
                                    <div style={{ fontSize: '13px', color: '#A8A29E', marginTop: '2px' }}>
                                        How was your walk?
                                    </div>
                                </div>
                            </div>

                            {/* Stars */}
                            <div style={{ display: 'flex', justifyContent: 'center', gap: '10px' }}>
                                {[1, 2, 3, 4, 5].map((star) => (
                                    <button
                                        key={star}
                                        onClick={() => setRating(star)}
                                        style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '4px' }}
                                    >
                                        <Star
                                            size={34}
                                            fill={star <= rating ? '#F97316' : 'transparent'}
                                            strokeWidth={1.8}
                                            style={{ color: star <= rating ? '#F97316' : '#D6D3D1' }}
                                        />
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>

                    {/* ── Done Button ───────────────────────────────────────── */}
                    <div style={{ padding: '0 20px 40px' }}>
                        <button
                            onClick={() => navigate('/home')}
                            style={{
                                width: '100%',
                                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                                background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
                                border: 'none', borderRadius: '20px', padding: '19px',
                                fontSize: '16px', fontWeight: 800, color: 'white',
                                cursor: 'pointer',
                                boxShadow: '0 10px 32px rgba(249,115,22,0.44)',
                                letterSpacing: '-0.2px',
                            }}
                        >
                            Back to Home
                            <ChevronRight size={18} strokeWidth={2.5} />
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
