import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ChevronLeft, Lock } from 'lucide-react';
import { BottomNav } from '../layout/BottomNav';

// ─── Types ───────────────────────────────────────────────────────────────────

type BadgeRarity = 'common' | 'rare' | 'epic' | 'legendary';

interface Badge {
  id: string;
  name: string;
  description: string;
  earned: boolean;
  earnedDate?: string;
  rarity: BadgeRarity;
  progress?: number;
  progressLabel?: string;
}

interface BadgeCategory {
  id: string;
  title: string;
  icon: string;
  badges: Badge[];
}

// ─── Rarity config ───────────────────────────────────────────────────────────

const RARITY: Record<BadgeRarity, { label: string; color: string; bg: string; glow: string }> = {
  common:    { label: 'Common',    color: '#78716C', bg: '#F5F5F4', glow: 'rgba(120,113,108,0.15)' },
  rare:      { label: 'Rare',      color: '#0EA5E9', bg: '#F0F9FF', glow: 'rgba(14,165,233,0.18)' },
  epic:      { label: 'Epic',      color: '#8B5CF6', bg: '#F5F3FF', glow: 'rgba(139,92,246,0.20)' },
  legendary: { label: 'Legendary', color: '#F97316', bg: '#FFF7ED', glow: 'rgba(249,115,22,0.22)' },
};

// ─── Mock data ────────────────────────────────────────────────────────────────

const CATEGORIES: BadgeCategory[] = [
  {
    id: 'milestones',
    title: 'Walk Milestones',
    icon: '👟',
    badges: [
      {
        id: 'first-walk',
        name: 'First Steps',
        description: 'Complete your very first walk session.',
        earned: true,
        earnedDate: 'Apr 20, 2026',
        rarity: 'common',
      },
      {
        id: '5-walks',
        name: 'Warming Up',
        description: 'Complete 5 walk sessions.',
        earned: true,
        earnedDate: 'Apr 27, 2026',
        rarity: 'common',
      },
      {
        id: '10-walks',
        name: 'On a Roll',
        description: 'Complete 10 walk sessions.',
        earned: false,
        rarity: 'rare',
        progress: 70,
        progressLabel: '7 / 10 walks',
      },
      {
        id: '50-walks',
        name: 'Power Walker',
        description: 'Complete 50 walk sessions.',
        earned: false,
        rarity: 'epic',
        progress: 14,
        progressLabel: '7 / 50 walks',
      },
      {
        id: '100-walks',
        name: 'Walk Legend',
        description: 'Complete 100 walk sessions.',
        earned: false,
        rarity: 'legendary',
        progress: 7,
        progressLabel: '7 / 100 walks',
      },
    ],
  },
  {
    id: 'distance',
    title: 'Distance Covered',
    icon: '📍',
    badges: [
      {
        id: '10km',
        name: 'Explorer',
        description: 'Walk a total of 10 km.',
        earned: true,
        earnedDate: 'Apr 25, 2026',
        rarity: 'common',
      },
      {
        id: '50km',
        name: 'Road Tripper',
        description: 'Walk a total of 50 km.',
        earned: false,
        rarity: 'rare',
        progress: 28,
        progressLabel: '14 / 50 km',
      },
      {
        id: '100km',
        name: 'Century Walker',
        description: 'Walk a total of 100 km.',
        earned: false,
        rarity: 'epic',
        progress: 14,
        progressLabel: '14 / 100 km',
      },
      {
        id: '500km',
        name: 'World Roamer',
        description: 'Walk a total of 500 km.',
        earned: false,
        rarity: 'legendary',
        progress: 3,
        progressLabel: '14 / 500 km',
      },
    ],
  },
  {
    id: 'social',
    title: 'Trust & Community',
    icon: '🤝',
    badges: [
      {
        id: 'trusted',
        name: 'Trusted Walker',
        description: 'Receive 3 five-star ratings.',
        earned: true,
        earnedDate: 'Apr 28, 2026',
        rarity: 'common',
      },
      {
        id: 'highly-trusted',
        name: 'Highly Trusted',
        description: 'Maintain a 4.8+ rating over 10 walks.',
        earned: true,
        earnedDate: 'Apr 28, 2026',
        rarity: 'rare',
      },
      {
        id: 'community-star',
        name: 'Community Star',
        description: 'Receive 25 five-star ratings.',
        earned: false,
        rarity: 'epic',
        progress: 12,
        progressLabel: '3 / 25 ratings',
      },
      {
        id: 'top-rated',
        name: 'Top Rated',
        description: 'Reach #1 on the weekly leaderboard.',
        earned: false,
        rarity: 'legendary',
        progress: 0,
        progressLabel: 'Not started',
      },
    ],
  },
  {
    id: 'streaks',
    title: 'Streaks',
    icon: '📅',
    badges: [
      {
        id: 'week-warrior',
        name: 'Week Warrior',
        description: 'Walk on 7 consecutive days.',
        earned: false,
        rarity: 'rare',
        progress: 43,
        progressLabel: '3 / 7 days',
      },
      {
        id: 'month-master',
        name: 'Month Master',
        description: 'Walk on 30 consecutive days.',
        earned: false,
        rarity: 'epic',
        progress: 10,
        progressLabel: '3 / 30 days',
      },
    ],
  },
  {
    id: 'special',
    title: 'Special Moments',
    icon: '✨',
    badges: [
      {
        id: 'early-bird',
        name: 'Early Bird',
        description: 'Complete a walk before 7:00 AM.',
        earned: true,
        earnedDate: 'Apr 27, 2026',
        rarity: 'common',
      },
      {
        id: 'night-owl',
        name: 'Night Owl',
        description: 'Complete a walk after 9:00 PM.',
        earned: false,
        rarity: 'rare',
        progress: 0,
        progressLabel: 'Not started',
      },
      {
        id: 'weekend-wanderer',
        name: 'Weekend Wanderer',
        description: 'Walk on both Saturday and Sunday.',
        earned: false,
        rarity: 'common',
        progress: 50,
        progressLabel: '1 / 2 days',
      },
      {
        id: 'park-hopper',
        name: 'Park Hopper',
        description: 'Walk in 5 different parks.',
        earned: false,
        rarity: 'epic',
        progress: 40,
        progressLabel: '2 / 5 parks',
      },
    ],
  },
];

// ─── Filter tabs ──────────────────────────────────────────────────────────────

type FilterKey = 'all' | 'earned' | 'locked';

const FILTER_TABS: { key: FilterKey; label: string }[] = [
  { key: 'all',    label: 'All' },
  { key: 'earned', label: 'Earned' },
  { key: 'locked', label: 'Locked' },
];

// ─── Badge Card ───────────────────────────────────────────────────────────────

function BadgeCard({ badge }: { badge: Badge }) {
  const [pressed, setPressed] = useState(false);
  const rarity = RARITY[badge.rarity];

  return (
    <div
      onMouseDown={() => setPressed(true)}
      onMouseUp={() => setPressed(false)}
      onMouseLeave={() => setPressed(false)}
      onTouchStart={() => setPressed(true)}
      onTouchEnd={() => setPressed(false)}
      style={{
        background: badge.earned ? 'white' : '#FAFAF9',
        borderRadius: '20px',
        border: badge.earned ? `1.5px solid ${rarity.bg === '#FFF7ED' ? '#FED7AA' : rarity.bg}` : '1.5px solid #F3F2F0',
        padding: '16px 16px 16px 18px',
        display: 'flex',
        alignItems: 'flex-start',
        gap: '12px',
        boxShadow: badge.earned
          ? `0 4px 20px ${rarity.glow}, 0 2px 6px rgba(0,0,0,0.04)`
          : '0 2px 6px rgba(0,0,0,0.03)',
        opacity: badge.earned ? 1 : 0.75,
        transform: pressed ? 'scale(0.98)' : 'scale(1)',
        transition: 'transform 0.12s ease, box-shadow 0.15s ease',
        cursor: 'default',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* Earned shimmer strip */}
      {badge.earned && (
        <div
          style={{
            position: 'absolute',
            top: 0, left: 0, right: 0,
            height: '3px',
            background: `linear-gradient(90deg, transparent, ${rarity.color}, transparent)`,
            opacity: 0.6,
          }}
        />
      )}

      {/* Rarity left accent bar */}
      <div
        style={{
          position: 'absolute',
          left: 0, top: '14px', bottom: '14px',
          width: '3px',
          borderRadius: '0 3px 3px 0',
          background: badge.earned ? rarity.color : '#E7E5E4',
        }}
      />

      {/* Text + meta */}
      <div style={{ flex: 1, minWidth: 0 }}>
        {/* Name + rarity + lock */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '7px', marginBottom: '4px', flexWrap: 'wrap' }}>
          <span
            style={{
              fontSize: '15px',
              fontWeight: 800,
              color: badge.earned ? '#1C1917' : '#A8A29E',
              letterSpacing: '-0.2px',
            }}
          >
            {badge.name}
          </span>
          <span
            style={{
              fontSize: '10px',
              fontWeight: 700,
              color: badge.earned ? rarity.color : '#C9C5C1',
              background: badge.earned ? rarity.bg : '#F5F5F4',
              borderRadius: '6px',
              padding: '2px 7px',
              textTransform: 'uppercase',
              letterSpacing: '0.4px',
            }}
          >
            {rarity.label}
          </span>
          {!badge.earned && (
            <Lock size={11} strokeWidth={2.5} style={{ color: '#C9C5C1', marginLeft: 'auto' }} />
          )}
        </div>

        {/* Description */}
        <p
          style={{
            fontSize: '12px',
            color: badge.earned ? '#78716C' : '#C9C5C1',
            lineHeight: 1.45,
            marginBottom: badge.earned ? '6px' : '8px',
          }}
        >
          {badge.description}
        </p>

        {/* Earned date OR progress bar */}
        {badge.earned ? (
          <span style={{ fontSize: '11px', color: '#A8A29E' }}>
            Earned {badge.earnedDate}
          </span>
        ) : (
          badge.progress !== undefined && (
            <div>
              <div style={{ marginBottom: '5px' }}>
                <span style={{ fontSize: '11px', color: '#A8A29E' }}>{badge.progressLabel}</span>
              </div>
              {/* Progress bar */}
              <div
                style={{
                  height: '5px',
                  background: '#F3F2F0',
                  borderRadius: '3px',
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    height: '100%',
                    width: `${badge.progress}%`,
                    background: `linear-gradient(90deg, ${rarity.color}88, ${rarity.color})`,
                    borderRadius: '3px',
                    transition: 'width 0.6s ease',
                  }}
                />
              </div>
            </div>
          )
        )}
      </div>
    </div>
  );
}

// ─── Main Screen ──────────────────────────────────────────────────────────────

export function BadgesScreen() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<FilterKey>('all');

  // Totals
  const allBadges = CATEGORIES.flatMap((c) => c.badges);
  const earnedCount = allBadges.filter((b) => b.earned).length;
  const totalCount = allBadges.length;
  const pct = Math.round((earnedCount / totalCount) * 100);

  const filteredCategories = CATEGORIES.map((cat) => ({
    ...cat,
    badges: cat.badges.filter((b) =>
      filter === 'all' ? true : filter === 'earned' ? b.earned : !b.earned,
    ),
  })).filter((cat) => cat.badges.length > 0);

  return (
    <div className="w-full h-full flex flex-col" style={{ background: '#FEF9F5' }}>

      {/* ── Sticky Header ─────────────────────────────────────── */}
      <div
        style={{
          background: 'white',
          borderBottom: '1px solid #F3F2F0',
          position: 'sticky',
          top: 0,
          zIndex: 20,
          boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
          padding: '52px 20px 0',
        }}
      >
        {/* Title row */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            marginBottom: '16px',
          }}
        >
          <button
            onClick={() => navigate('/profile')}
            style={{
              width: '40px', height: '40px', background: '#F5F5F4',
              borderRadius: '12px', border: 'none', cursor: 'pointer',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <ChevronLeft size={20} style={{ color: '#1C1917' }} strokeWidth={2.5} />
          </button>
          <div style={{ flex: 1 }}>
            <h1 style={{ fontSize: '20px', fontWeight: 800, color: '#1C1917', letterSpacing: '-0.3px' }}>
              My Badges
            </h1>
            <p style={{ fontSize: '12px', color: '#A8A29E', marginTop: '1px' }}>
              {earnedCount} of {totalCount} earned
            </p>
          </div>
        </div>

        {/* Filter tabs */}
        <div style={{ display: 'flex', borderTop: '1px solid #F3F2F0' }}>
          {FILTER_TABS.map((tab) => {
            const isActive = filter === tab.key;
            return (
              <button
                key={tab.key}
                onClick={() => setFilter(tab.key)}
                style={{
                  flex: 1,
                  padding: '12px 6px',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  borderBottom: isActive ? '2.5px solid #F97316' : '2.5px solid transparent',
                  marginBottom: '-1px',
                }}
              >
                <span
                  style={{
                    fontSize: '13px',
                    fontWeight: isActive ? 800 : 600,
                    color: isActive ? '#F97316' : '#A8A29E',
                    transition: 'color 0.15s',
                  }}
                >
                  {tab.label}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {/* ── Scrollable Body ───────────────────────────────────── */}
      <div className="flex-1 overflow-y-auto mobile-scroll" style={{ padding: '18px 16px 96px' }}>

        {/* Overall progress card */}
        <div
          style={{
            background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
            borderRadius: '22px',
            padding: '20px',
            marginBottom: '22px',
            boxShadow: '0 8px 28px rgba(249,115,22,0.38)',
            position: 'relative',
            overflow: 'hidden',
          }}
        >
          {/* Decorative circle */}
          <div
            style={{
              position: 'absolute',
              right: '-28px',
              top: '-28px',
              width: '110px',
              height: '110px',
              borderRadius: '50%',
              background: 'rgba(255,255,255,0.1)',
            }}
          />
          <div
            style={{
              position: 'absolute',
              right: '24px',
              bottom: '-36px',
              width: '80px',
              height: '80px',
              borderRadius: '50%',
              background: 'rgba(255,255,255,0.07)',
            }}
          />

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px' }}>
            <div>
              <div style={{ fontSize: '13px', fontWeight: 600, color: 'rgba(255,255,255,0.8)', marginBottom: '4px' }}>
                Collection Progress
              </div>
              <div style={{ fontSize: '32px', fontWeight: 800, color: 'white', letterSpacing: '-0.5px', lineHeight: 1 }}>
                {pct}%
              </div>
              <div style={{ fontSize: '12px', color: 'rgba(255,255,255,0.75)', marginTop: '4px' }}>
                {earnedCount} / {totalCount} badges
              </div>
            </div>
          </div>

          {/* Progress bar */}
          <div style={{ height: '8px', background: 'rgba(255,255,255,0.25)', borderRadius: '4px', overflow: 'hidden' }}>
            <div
              style={{
                height: '100%',
                width: `${pct}%`,
                background: 'white',
                borderRadius: '4px',
                transition: 'width 0.7s ease',
              }}
            />
          </div>
        </div>

        {/* Rarity legend */}
        <div
          style={{
            display: 'flex',
            gap: '8px',
            overflowX: 'auto',
            marginBottom: '20px',
            paddingBottom: '2px',
          }}
        >
          {(Object.entries(RARITY) as [BadgeRarity, typeof RARITY[BadgeRarity]][]).map(([key, val]) => (
            <div
              key={key}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '5px',
                background: val.bg,
                borderRadius: '20px',
                padding: '5px 11px',
                flexShrink: 0,
                border: `1px solid ${val.color}22`,
              }}
            >
              <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: val.color }} />
              <span style={{ fontSize: '11px', fontWeight: 700, color: val.color }}>{val.label}</span>
            </div>
          ))}
        </div>

        {/* Badge categories */}
        {filteredCategories.length === 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', paddingTop: '40px' }}>
            <div style={{ fontSize: '48px', marginBottom: '14px' }}>
              {filter === 'earned' ? '🏅' : '🔒'}
            </div>
            <p style={{ fontSize: '16px', fontWeight: 700, color: '#78716C', marginBottom: '6px' }}>
              {filter === 'earned' ? 'No badges earned yet' : 'All badges unlocked!'}
            </p>
            <p style={{ fontSize: '13px', color: '#A8A29E', textAlign: 'center', lineHeight: 1.5 }}>
              {filter === 'earned'
                ? 'Complete walk sessions to start collecting badges.'
                : 'You\'ve earned every badge — amazing work!'}
            </p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '28px' }}>
            {filteredCategories.map((category) => (
              <div key={category.id}>
                {/* Category header */}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    marginBottom: '12px',
                    paddingLeft: '2px',
                  }}
                >
                  <span style={{ fontSize: '18px' }}>{category.icon}</span>
                  <span
                    style={{
                      fontSize: '15px',
                      fontWeight: 800,
                      color: '#1C1917',
                      letterSpacing: '-0.2px',
                    }}
                  >
                    {category.title}
                  </span>
                  <span
                    style={{
                      fontSize: '11px',
                      fontWeight: 700,
                      color: '#A8A29E',
                      marginLeft: '2px',
                    }}
                  >
                    {category.badges.filter((b) => b.earned).length}/{category.badges.length}
                  </span>
                </div>

                {/* Badge list */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {category.badges.map((badge) => (
                    <BadgeCard key={badge.id} badge={badge} />
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <BottomNav />
    </div>
  );
}