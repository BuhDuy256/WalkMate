import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ChevronLeft, UserCheck, ShieldCheck } from 'lucide-react';
import { BottomNav } from '../layout/BottomNav';

type FriendTab = 'friends' | 'incoming' | 'sent';

const AVATAR_URL =
  'https://images.unsplash.com/photo-1761933808230-9a2e78956daa?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHx5b3VuZyUyMGFzaWFuJTIwd29tYW4lMjBwb3J0cmFpdCUyMHNtaWxpbmd8ZW58MXx8fHwxNzc3NDMyODUzfDA&ixlib=rb-4.1.0&q=80&w=400';

interface Friend {
  id: number;
  name: string;
  trust: number;
  initials: string;
  color: string;
  avatarUrl?: string;
}

interface FriendRequest {
  id: number;
  name: string;
  initials: string;
  color: string;
  avatarUrl?: string;
  mutualFriends?: number;
}

interface SentRequest {
  id: number;
  name: string;
  initials: string;
  color: string;
  sentAt: string;
}

const FRIENDS: Friend[] = [
  { id: 1, name: 'Luân Trần', trust: 545, initials: 'LT', color: '#F97316', avatarUrl: AVATAR_URL },
  { id: 2, name: 'Nguyen Minh', trust: 340, initials: 'NM', color: '#7C3AED' },
  { id: 3, name: 'Tran Linh', trust: 210, initials: 'TL', color: '#0EA5E9' },
];

const INCOMING: FriendRequest[] = [
  { id: 1, name: 'Luân Trần', initials: 'LT', color: '#F97316', avatarUrl: AVATAR_URL, mutualFriends: 2 },
  { id: 2, name: 'Hoa Nguyen', initials: 'HN', color: '#EC4899', mutualFriends: 0 },
];

const SENT: SentRequest[] = [
  { id: 1, name: 'Pham Quang', initials: 'PQ', color: '#6366F1', sentAt: '2 days ago' },
];

export function FriendsScreen() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<FriendTab>('friends');
  const [incomingList, setIncomingList] = useState(INCOMING);
  const [sentList, setSentList] = useState(SENT);
  const [friendsList, setFriendsList] = useState(FRIENDS);

  const handleAccept = (id: number) => {
    const req = incomingList.find((r) => r.id === id);
    if (req) {
      setFriendsList((prev) => [
        ...prev,
        { id: req.id + 100, name: req.name, trust: 0, initials: req.initials, color: req.color, avatarUrl: req.avatarUrl },
      ]);
      setIncomingList((prev) => prev.filter((r) => r.id !== id));
    }
  };

  const handleDecline = (id: number) => {
    setIncomingList((prev) => prev.filter((r) => r.id !== id));
  };

  const handleRemoveFriend = (id: number) => {
    setFriendsList((prev) => prev.filter((f) => f.id !== id));
  };

  const handleCancelSent = (id: number) => {
    setSentList((prev) => prev.filter((s) => s.id !== id));
  };

  const tabs: { key: FriendTab; label: string; count?: number }[] = [
    { key: 'friends', label: 'Friends', count: friendsList.length },
    { key: 'incoming', label: 'Incoming', count: incomingList.length },
    { key: 'sent', label: 'Sent', count: sentList.length > 0 ? sentList.length : undefined },
  ];

  return (
    <div className="w-full h-full flex flex-col" style={{ background: '#FEF9F5' }}>
      {/* Header */}
      <div
        className="flex items-center gap-3 px-5 pt-14 pb-0"
        style={{
          background: 'white',
          position: 'sticky', top: 0, zIndex: 20,
          boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
        }}
      >
        <button
          onClick={() => navigate('/profile')}
          style={{
            width: '40px', height: '40px', background: '#F5F5F4',
            borderRadius: '12px', border: 'none', cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0,
            marginBottom: '12px',
          }}
        >
          <ChevronLeft size={20} style={{ color: '#1C1917' }} strokeWidth={2.5} />
        </button>

        {/* Tabs */}
        <div className="flex flex-1 gap-0" style={{ marginLeft: '8px' }}>
          {tabs.map((tab) => {
            const isActive = activeTab === tab.key;
            return (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                style={{
                  flex: 1, paddingBottom: '14px', paddingTop: '0',
                  border: 'none', background: 'transparent',
                  fontSize: '14px', fontWeight: isActive ? 700 : 500,
                  color: isActive ? '#F97316' : '#A8A29E',
                  borderBottom: isActive ? '2.5px solid #F97316' : '2.5px solid transparent',
                  cursor: 'pointer', transition: 'all 0.2s',
                  fontFamily: 'Inter, sans-serif',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '4px',
                }}
              >
                {tab.label}
                {tab.count !== undefined && tab.count > 0 && (
                  <span
                    style={{
                      background: isActive ? '#F97316' : '#E7E5E4',
                      color: isActive ? 'white' : '#78716C',
                      borderRadius: '100px',
                      fontSize: '11px',
                      fontWeight: 700,
                      padding: '1px 6px',
                      minWidth: '18px',
                      textAlign: 'center',
                    }}
                  >
                    {tab.count}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto mobile-scroll" style={{ padding: '16px 16px 88px' }}>

        {/* ── FRIENDS TAB ── */}
        {activeTab === 'friends' && (
          <div className="flex flex-col gap-3">
            {friendsList.length === 0 ? (
              <div
                className="flex flex-col items-center"
                style={{ paddingTop: '60px', color: '#A8A29E' }}
              >
                <UserCheck size={40} strokeWidth={1.5} style={{ color: '#D6D3D1', marginBottom: '12px' }} />
                <p style={{ fontSize: '15px', fontWeight: 600, color: '#78716C' }}>No friends yet</p>
                <p style={{ fontSize: '13px', color: '#A8A29E', marginTop: '4px' }}>
                  Accept incoming requests to add friends
                </p>
              </div>
            ) : (
              friendsList.map((friend) => (
                <div
                  key={friend.id}
                  style={{
                    background: 'white', borderRadius: '20px',
                    border: '1.5px solid #F3F2F0',
                    boxShadow: '0 4px 16px rgba(0,0,0,0.06)',
                    padding: '14px 16px',
                  }}
                >
                  <div className="flex items-center gap-3 mb-3">
                    {/* Avatar */}
                    {friend.avatarUrl ? (
                      <div
                        style={{
                          width: '48px', height: '48px', borderRadius: '50%',
                          overflow: 'hidden', flexShrink: 0,
                          border: '2px solid white',
                          boxShadow: '0 3px 10px rgba(0,0,0,0.12)',
                        }}
                      >
                        <img src={friend.avatarUrl} alt={friend.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                      </div>
                    ) : (
                      <div
                        style={{
                          width: '48px', height: '48px', borderRadius: '50%',
                          background: friend.color,
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                          flexShrink: 0,
                          boxShadow: `0 3px 10px ${friend.color}55`,
                        }}
                      >
                        <span style={{ fontSize: '15px', fontWeight: 700, color: 'white' }}>{friend.initials}</span>
                      </div>
                    )}
                    <div className="flex-1">
                      <div style={{ fontSize: '15px', fontWeight: 700, color: '#1C1917' }}>{friend.name}</div>
                      <div className="flex items-center gap-1 mt-0.5">
                        <ShieldCheck size={12} style={{ color: '#F97316' }} strokeWidth={2} />
                        <span style={{ fontSize: '12px', color: '#78716C', fontWeight: 500 }}>
                          Trust: {friend.trust}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Action buttons */}
                  <div className="flex flex-col gap-2">
                    <button
                      style={{
                        width: '100%', padding: '10px',
                        background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
                        border: 'none', borderRadius: '100px',
                        fontSize: '13px', fontWeight: 700, color: 'white',
                        cursor: 'pointer',
                        boxShadow: '0 4px 14px rgba(249,115,22,0.38)',
                      }}
                    >
                      Invite Walk
                    </button>
                    <button
                      onClick={() => navigate('/public-profile', { state: { friend } })}
                      style={{
                        width: '100%', padding: '10px',
                        background: 'white',
                        border: '1.5px solid #F97316',
                        borderRadius: '100px',
                        fontSize: '13px', fontWeight: 700, color: '#F97316',
                        cursor: 'pointer',
                      }}
                    >
                      View Profile
                    </button>
                    <button
                      onClick={() => handleRemoveFriend(friend.id)}
                      style={{
                        width: '100%', padding: '10px',
                        background: 'white',
                        border: '1.5px solid #E7E5E4',
                        borderRadius: '100px',
                        fontSize: '13px', fontWeight: 600, color: '#78716C',
                        cursor: 'pointer',
                      }}
                    >
                      Remove
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {/* ── INCOMING TAB ── */}
        {activeTab === 'incoming' && (
          <div className="flex flex-col gap-3">
            {incomingList.length === 0 ? (
              <div
                className="flex flex-col items-center"
                style={{ paddingTop: '60px' }}
              >
                <UserCheck size={40} strokeWidth={1.5} style={{ color: '#D6D3D1', marginBottom: '12px' }} />
                <p style={{ fontSize: '15px', fontWeight: 600, color: '#78716C' }}>No incoming requests</p>
                <p style={{ fontSize: '13px', color: '#A8A29E', marginTop: '4px' }}>
                  When someone adds you, they'll appear here
                </p>
              </div>
            ) : (
              incomingList.map((req) => (
                <div
                  key={req.id}
                  style={{
                    background: 'white', borderRadius: '20px',
                    border: '1.5px solid #F3F2F0',
                    boxShadow: '0 4px 16px rgba(0,0,0,0.06)',
                    padding: '14px 16px',
                    display: 'flex', alignItems: 'center', gap: '12px',
                  }}
                >
                  {/* Avatar */}
                  {req.avatarUrl ? (
                    <div
                      style={{
                        width: '52px', height: '52px', borderRadius: '50%',
                        overflow: 'hidden', flexShrink: 0,
                        border: '2px solid white',
                        boxShadow: '0 3px 10px rgba(0,0,0,0.12)',
                      }}
                    >
                      <img src={req.avatarUrl} alt={req.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                    </div>
                  ) : (
                    <div
                      style={{
                        width: '52px', height: '52px', borderRadius: '50%',
                        background: req.color,
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        flexShrink: 0,
                        boxShadow: `0 3px 10px ${req.color}55`,
                      }}
                    >
                      <span style={{ fontSize: '16px', fontWeight: 700, color: 'white' }}>{req.initials}</span>
                    </div>
                  )}

                  {/* Name */}
                  <div className="flex-1">
                    <div style={{ fontSize: '15px', fontWeight: 700, color: '#1C1917' }}>{req.name}</div>
                    {req.mutualFriends ? (
                      <div style={{ fontSize: '12px', color: '#A8A29E', marginTop: '2px' }}>
                        {req.mutualFriends} mutual friend{req.mutualFriends > 1 ? 's' : ''}
                      </div>
                    ) : null}
                  </div>

                  {/* Accept / Decline */}
                  <div className="flex flex-col gap-2">
                    <button
                      onClick={() => handleAccept(req.id)}
                      style={{
                        padding: '8px 20px',
                        background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
                        border: 'none', borderRadius: '100px',
                        fontSize: '13px', fontWeight: 700, color: 'white',
                        cursor: 'pointer',
                        boxShadow: '0 4px 12px rgba(249,115,22,0.38)',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      Accept
                    </button>
                    <button
                      onClick={() => handleDecline(req.id)}
                      style={{
                        padding: '8px 20px',
                        background: 'white',
                        border: '1.5px solid #F97316',
                        borderRadius: '100px',
                        fontSize: '13px', fontWeight: 600, color: '#F97316',
                        cursor: 'pointer',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      Decline
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {/* ── SENT TAB ── */}
        {activeTab === 'sent' && (
          <div className="flex flex-col gap-3">
            {sentList.length === 0 ? (
              <div
                className="flex flex-col items-center"
                style={{ paddingTop: '60px' }}
              >
                <UserCheck size={40} strokeWidth={1.5} style={{ color: '#D6D3D1', marginBottom: '12px' }} />
                <p style={{ fontSize: '15px', fontWeight: 600, color: '#78716C' }}>No sent requests</p>
                <p style={{ fontSize: '13px', color: '#A8A29E', marginTop: '4px' }}>
                  Requests you've sent will show up here
                </p>
              </div>
            ) : (
              sentList.map((req) => (
                <div
                  key={req.id}
                  style={{
                    background: 'white', borderRadius: '20px',
                    border: '1.5px solid #F3F2F0',
                    boxShadow: '0 4px 16px rgba(0,0,0,0.06)',
                    padding: '14px 16px',
                    display: 'flex', alignItems: 'center', gap: '12px',
                  }}
                >
                  <div
                    style={{
                      width: '48px', height: '48px', borderRadius: '50%',
                      background: req.color,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      flexShrink: 0,
                      boxShadow: `0 3px 10px ${req.color}55`,
                    }}
                  >
                    <span style={{ fontSize: '15px', fontWeight: 700, color: 'white' }}>{req.initials}</span>
                  </div>
                  <div className="flex-1">
                    <div style={{ fontSize: '15px', fontWeight: 700, color: '#1C1917' }}>{req.name}</div>
                    <div style={{ fontSize: '12px', color: '#A8A29E', marginTop: '2px' }}>
                      Pending · Sent {req.sentAt}
                    </div>
                  </div>
                  <button
                    onClick={() => handleCancelSent(req.id)}
                    style={{
                      padding: '8px 16px',
                      background: 'white',
                      border: '1.5px solid #E7E5E4',
                      borderRadius: '100px',
                      fontSize: '12px', fontWeight: 600, color: '#78716C',
                      cursor: 'pointer',
                    }}
                  >
                    Cancel
                  </button>
                </div>
              ))
            )}
          </div>
        )}
      </div>

      <BottomNav />
    </div>
  );
}
