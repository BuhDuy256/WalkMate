import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router';
import { ChevronLeft, Star, Info, Check } from 'lucide-react';
import { BottomNav } from '../layout/BottomNav';

const WELL_OPTIONS = [
  'Matched walking pace',
  'Punctual',
  'Enjoyable chat',
  'Safe and respectful',
  'Motivating',
  'Good route suggestions',
];

export function LeaveReviewScreen() {
  const navigate = useNavigate();
  const location = useLocation();
  const partner = location.state?.partner;
  const partnerName = partner?.name ?? 'your walk partner';

  const [rating, setRating] = useState(5);
  const [hoveredStar, setHoveredStar] = useState<number | null>(null);
  const [selectedOptions, setSelectedOptions] = useState<string[]>(['Matched walking pace', 'Punctual', 'Enjoyable chat']);
  const [comment, setComment] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const toggleOption = (opt: string) => {
    setSelectedOptions((prev) =>
      prev.includes(opt) ? prev.filter((o) => o !== opt) : [...prev, opt]
    );
  };

  const handleSubmit = () => {
    setSubmitted(true);
    setTimeout(() => {
      navigate('/walk-history');
    }, 900);
  };

  const displayRating = hoveredStar ?? rating;

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
        <h2
          style={{
            flex: 1, textAlign: 'center',
            fontSize: '18px', fontWeight: 800, color: '#1C1917', letterSpacing: '-0.3px',
          }}
        >
          Leave a Review
        </h2>
        <div style={{ width: 40 }} />
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto mobile-scroll" style={{ padding: '24px 20px 100px' }}>

        {/* Rating section */}
        <div className="mb-6">
          <p style={{ fontSize: '14px', fontWeight: 700, color: '#1C1917', marginBottom: '14px' }}>
            Rating
          </p>

          {/* Star row */}
          <div className="flex gap-2 mb-4">
            {[1, 2, 3, 4, 5].map((star) => (
              <button
                key={star}
                onClick={() => setRating(star)}
                onMouseEnter={() => setHoveredStar(star)}
                onMouseLeave={() => setHoveredStar(null)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '2px' }}
              >
                <Star
                  size={40}
                  fill={star <= displayRating ? '#6C63FF' : 'transparent'}
                  style={{
                    color: star <= displayRating ? '#6C63FF' : '#D6D3D1',
                    transition: 'all 0.15s',
                    transform: star <= displayRating ? 'scale(1.05)' : 'scale(1)',
                  }}
                  strokeWidth={1.5}
                />
              </button>
            ))}
          </div>

          {/* Info note */}
          <div
            className="flex items-start gap-2"
            style={{
              background: '#F8F7FF',
              borderRadius: '12px',
              padding: '12px 14px',
              border: '1px solid #E8E6FF',
            }}
          >
            <Info size={14} style={{ color: '#6C63FF', flexShrink: 0, marginTop: '1px' }} strokeWidth={2} />
            <p style={{ fontSize: '12px', color: '#78716C', lineHeight: 1.55 }}>
              Your 1–5 star rating directly impacts this user's Trust Score. Higher ratings reward reliability; lower ratings reflect genuine concerns. Please be fair and objective.
            </p>
          </div>
        </div>

        {/* What went well */}
        <div className="mb-6">
          <p style={{ fontSize: '14px', fontWeight: 700, color: '#1C1917', marginBottom: '12px' }}>
            What went well?
          </p>
          <div className="flex flex-wrap gap-2.5">
            {WELL_OPTIONS.map((opt) => {
              const active = selectedOptions.includes(opt);
              return (
                <button
                  key={opt}
                  onClick={() => toggleOption(opt)}
                  style={{
                    padding: '8px 16px',
                    borderRadius: '100px',
                    border: active ? '1.5px solid #6C63FF' : '1.5px solid #E7E5E4',
                    background: active ? '#F8F7FF' : 'white',
                    fontSize: '13px',
                    fontWeight: active ? 700 : 500,
                    color: active ? '#6C63FF' : '#44403C',
                    cursor: 'pointer',
                    transition: 'all 0.18s',
                    boxShadow: active ? '0 3px 10px rgba(108,99,255,0.2)' : '0 1px 3px rgba(0,0,0,0.06)',
                  }}
                >
                  {opt}
                </button>
              );
            })}
          </div>
        </div>

        {/* Comment */}
        <div className="mb-8">
          <p style={{ fontSize: '14px', fontWeight: 700, color: '#1C1917', marginBottom: '12px' }}>
            Comment (optional)
          </p>
          <textarea
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder={`Tell others about walking with ${partnerName}...`}
            rows={4}
            style={{
              width: '100%',
              background: 'transparent',
              border: 'none',
              borderBottom: '1.5px solid #D6D3D1',
              padding: '8px 0',
              fontSize: '15px', color: '#1C1917',
              fontFamily: 'Inter, sans-serif',
              outline: 'none',
              resize: 'none',
              lineHeight: 1.6,
            }}
          />
        </div>

        {/* Submit button */}
        <button
          onClick={handleSubmit}
          disabled={submitted}
          style={{
            width: '100%', padding: '17px',
            background: submitted
              ? 'linear-gradient(135deg, #22C55E 0%, #16A34A 100%)'
              : 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
            border: 'none', borderRadius: '100px',
            fontSize: '16px', fontWeight: 800, color: 'white',
            cursor: submitted ? 'default' : 'pointer',
            boxShadow: submitted
              ? '0 8px 24px rgba(34,197,94,0.4)'
              : '0 8px 24px rgba(249,115,22,0.4)',
            transition: 'all 0.3s',
            letterSpacing: '-0.2px',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
          }}
        >
          {submitted ? (
            <>
              <Check size={18} strokeWidth={2.5} />
              Review Submitted!
            </>
          ) : (
            'Submit Review'
          )}
        </button>
      </div>

      <BottomNav />
    </div>
  );
}
