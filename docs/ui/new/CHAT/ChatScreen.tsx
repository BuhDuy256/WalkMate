import { useState, useRef, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router';
import { ChevronLeft, Send, ImagePlus, MapPin } from 'lucide-react';

// ── Types ───────────────────────────────────────────────────────────────────
interface Message {
  id: number;
  text: string;
  from: 'me' | 'them';
  time: string;
  read?: boolean;
}

interface Partner {
  name: string;
  initials: string;
  color: string;
}

// ── Mock conversation seeded with session context ───────────────────────────
const SEED_MESSAGES: Message[] = [
  { id: 1,  from: 'them', text: 'Hey! Just confirmed our walk session 🏃',           time: '08:12' },
  { id: 2,  from: 'me',   text: 'Awesome! I\'m looking forward to it 😊',            time: '08:13', read: true },
  { id: 3,  from: 'them', text: 'Should we meet at the main entrance?',              time: '08:14' },
  { id: 4,  from: 'me',   text: 'Sure! Near the big fountain, right?',               time: '08:15', read: true },
  { id: 5,  from: 'them', text: 'Exactly 👍 I\'ll bring some water, it\'s hot today', time: '08:16' },
  { id: 6,  from: 'me',   text: 'Good idea. See you at 7 AM!',                       time: '08:17', read: true },
  { id: 7,  from: 'them', text: 'That was such a great walk! 🌿',                    time: '09:45' },
  { id: 8,  from: 'me',   text: 'Totally agree, the park was beautiful this morning',time: '09:46', read: true },
  { id: 9,  from: 'them', text: 'We should do this again next week!',                time: '09:47' },
  { id: 10, from: 'me',   text: 'Definitely 🙌 I\'ll create a new intent',           time: '09:48', read: true },
];

const DEFAULT_PARTNER: Partner = {
  name: 'Bảo Duy Nguyễn',
  initials: 'BD',
  color: '#7C3AED',
};

// ── Helpers ─────────────────────────────────────────────────────────────────
function now(): string {
  return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
}

// ── Chat bubble ──────────────────────────────────────────────────────────────
function Bubble({ msg }: { msg: Message }) {
  const isMe = msg.from === 'me';
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: isMe ? 'row-reverse' : 'row',
        alignItems: 'flex-end',
        gap: '8px',
        maxWidth: '100%',
      }}
    >
      {/* Timestamp + read */}
      <span
        style={{
          fontSize: '10px',
          color: '#C9C5C1',
          whiteSpace: 'nowrap',
          marginBottom: '4px',
          flexShrink: 0,
        }}
      >
        {msg.time}
        {isMe && msg.read && (
          <span style={{ color: '#F97316', marginLeft: '3px' }}>✓✓</span>
        )}
      </span>

      {/* Bubble */}
      <div
        style={{
          maxWidth: '68%',
          padding: '11px 15px',
          borderRadius: isMe ? '18px 18px 4px 18px' : '18px 18px 18px 4px',
          background: isMe
            ? 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)'
            : 'white',
          color: isMe ? 'white' : '#1C1917',
          fontSize: '14px',
          fontWeight: 500,
          lineHeight: 1.45,
          boxShadow: isMe
            ? '0 4px 16px rgba(249,115,22,0.28)'
            : '0 2px 10px rgba(0,0,0,0.07)',
          border: isMe ? 'none' : '1.5px solid #F3F2F0',
        }}
      >
        {msg.text}
      </div>
    </div>
  );
}

// ── Main Screen ──────────────────────────────────────────────────────────────
export function ChatScreen() {
  const navigate  = useNavigate();
  const location  = useLocation();
  const state     = (location.state as any) ?? {};
  const partner: Partner = state.partner ?? DEFAULT_PARTNER;
  const sessionLocation: string = state.location ?? 'Le Van Tam Park';

  const [messages, setMessages] = useState<Message[]>(SEED_MESSAGES);
  const [draft, setDraft]       = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef  = useRef<HTMLInputElement>(null);

  // Always scroll to latest message
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = () => {
    const text = draft.trim();
    if (!text) return;
    setMessages((prev) => [
      ...prev,
      { id: Date.now(), from: 'me', text, time: now(), read: false },
    ]);
    setDraft('');
    inputRef.current?.focus();

    // Simulate reply after short delay
    setTimeout(() => {
      const replies = [
        'Sounds great! 😊',
        'Haha, agreed!',
        'Let\'s do it again soon 🏃',
        'I had so much fun walking with you!',
        '👍',
        'See you next time!',
      ];
      const pick = replies[Math.floor(Math.random() * replies.length)];
      setMessages((prev) => [
        ...prev,
        { id: Date.now() + 1, from: 'them', text: pick, time: now() },
      ]);
    }, 1200);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div
      className="w-full h-full flex flex-col"
      style={{ background: '#FEF9F5', position: 'relative' }}
    >

      {/* ── Header ──────────────────────────────────────────────── */}
      <div
        style={{
          background: 'white',
          borderBottom: '1px solid #F3F2F0',
          boxShadow: '0 2px 12px rgba(0,0,0,0.06)',
          padding: '52px 16px 14px',
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          flexShrink: 0,
          zIndex: 20,
        }}
      >
        {/* Back */}
        <button
          onClick={() => navigate(-1)}
          style={{
            width: '40px', height: '40px', background: '#F5F5F4',
            borderRadius: '12px', border: 'none', cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <ChevronLeft size={20} style={{ color: '#1C1917' }} strokeWidth={2.5} />
        </button>

        {/* Avatar */}
        <div style={{ position: 'relative', flexShrink: 0 }}>
          <div
            style={{
              width: '42px', height: '42px', borderRadius: '14px',
              background: partner.color,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: `0 4px 12px ${partner.color}55`,
            }}
          >
            <span style={{ fontSize: '15px', fontWeight: 700, color: 'white' }}>
              {partner.initials}
            </span>
          </div>
          {/* Online dot */}
          <div
            style={{
              position: 'absolute', bottom: '-1px', right: '-1px',
              width: '11px', height: '11px', borderRadius: '50%',
              background: '#22C55E', border: '2px solid white',
            }}
          />
        </div>

        {/* Name + context */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              fontSize: '16px', fontWeight: 800, color: '#1C1917',
              letterSpacing: '-0.2px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
            }}
          >
            {partner.name}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', marginTop: '1px' }}>
            <MapPin size={10} style={{ color: '#F97316' }} strokeWidth={2} />
            <span style={{ fontSize: '11px', color: '#A8A29E', fontWeight: 500 }}>
              {sessionLocation}
            </span>
          </div>
        </div>
      </div>

      {/* ── Date chip ────────────────────────────────────────────── */}
      <div style={{ display: 'flex', justifyContent: 'center', padding: '14px 0 6px' }}>
        <div
          style={{
            background: '#F5F5F4', borderRadius: '20px',
            padding: '4px 14px',
          }}
        >
          <span style={{ fontSize: '11px', fontWeight: 600, color: '#A8A29E' }}>
            Today · Walk Session
          </span>
        </div>
      </div>

      {/* ── Messages ─────────────────────────────────────────────── */}
      <div
        className="flex-1 overflow-y-auto mobile-scroll"
        style={{ padding: '8px 16px 16px', display: 'flex', flexDirection: 'column', gap: '10px' }}
      >
        {messages.map((msg) => (
          <Bubble key={msg.id} msg={msg} />
        ))}
        <div ref={bottomRef} />
      </div>

      {/* ── Input Bar ────────────────────────────────────────────── */}
      <div
        style={{
          background: 'white',
          borderTop: '1px solid #F3F2F0',
          padding: '12px 14px 28px',
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          flexShrink: 0,
          boxShadow: '0 -4px 20px rgba(0,0,0,0.06)',
        }}
      >
        {/* Attachment */}
        <button
          style={{
            width: '42px', height: '42px', borderRadius: '13px',
            background: '#F5F5F4', border: 'none', cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <ImagePlus size={18} style={{ color: '#A8A29E' }} strokeWidth={2} />
        </button>

        {/* Input pill */}
        <div
          style={{
            flex: 1,
            background: '#F5F5F4',
            borderRadius: '22px',
            border: draft ? '1.5px solid #F97316' : '1.5px solid transparent',
            display: 'flex',
            alignItems: 'center',
            padding: '0 14px',
            transition: 'border-color 0.15s',
          }}
        >
          <input
            ref={inputRef}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Type a message…"
            style={{
              flex: 1,
              background: 'transparent',
              border: 'none',
              outline: 'none',
              fontSize: '14px',
              fontWeight: 500,
              color: '#1C1917',
              padding: '11px 0',
              fontFamily: 'Inter, sans-serif',
            }}
          />
        </div>

        {/* Send */}
        <button
          onClick={handleSend}
          disabled={!draft.trim()}
          style={{
            width: '42px', height: '42px', borderRadius: '13px',
            background: draft.trim()
              ? 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)'
              : '#F3F2F0',
            border: 'none',
            cursor: draft.trim() ? 'pointer' : 'default',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0,
            boxShadow: draft.trim() ? '0 4px 14px rgba(249,115,22,0.35)' : 'none',
            transition: 'all 0.18s',
          }}
        >
          <Send
            size={17}
            strokeWidth={2.5}
            style={{
              color: draft.trim() ? 'white' : '#C9C5C1',
              transform: 'translateX(1px)',
            }}
          />
        </button>
      </div>
    </div>
  );
}
