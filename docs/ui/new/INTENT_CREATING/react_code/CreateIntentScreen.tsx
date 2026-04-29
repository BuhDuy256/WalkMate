import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router';
import { ChevronLeft, User, MapPin, Calendar, Clock, Users, Lock, Sparkles, Settings2 } from 'lucide-react';
import * as Slider from '@radix-ui/react-slider';
import * as Switch from '@radix-ui/react-switch';

const DEFAULT_HOTSPOT = {
  id: 2,
  name: 'Independence Palace Square',
  lat: 10.7769,
  lng: 106.6956,
};

const today = new Date();
const formattedDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;

function formatHour(h: number): string {
  return `${String(h).padStart(2, '0')}:00`;
}

type Gender = 'Male' | 'Female' | 'Any';

export function CreateIntentScreen() {
  const navigate = useNavigate();
  const location = useLocation();
  const hotspot = (location.state as any)?.hotspot || DEFAULT_HOTSPOT;

  const [timeRange, setTimeRange] = useState([18, 21]);
  const [ageRange, setAgeRange] = useState([20, 32]);
  const [gender, setGender] = useState<Gender>('Any');
  const [isPrivate, setIsPrivate] = useState(false);
  const [finding, setFinding] = useState(false);

  const handleFindMatch = () => {
    setFinding(true);
    setTimeout(() => {
      setFinding(false);
      navigate('/invitations');
    }, 2000);
  };

  return (
    <div
      className="w-full h-full flex flex-col mobile-scroll overflow-y-auto"
      style={{ background: '#FEF9F5' }}
    >
      {/* Header */}
      <div
        className="flex items-center gap-3 px-5 pt-14 pb-4"
        style={{
          background: 'white',
          borderBottom: '1px solid #F3F2F0',
          position: 'sticky',
          top: 0,
          zIndex: 10,
          boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
        }}
      >
        <button
          onClick={() => navigate('/hotspot', { state: { hotspot } })}
          className="flex items-center justify-center"
          style={{
            width: '40px',
            height: '40px',
            background: '#F5F5F4',
            borderRadius: '12px',
            border: 'none',
            cursor: 'pointer',
            flexShrink: 0,
          }}
        >
          <ChevronLeft size={20} style={{ color: '#1C1917' }} strokeWidth={2.5} />
        </button>

        <div className="flex items-center gap-3 flex-1 min-w-0">
          <div
            className="flex items-center justify-center flex-shrink-0"
            style={{
              width: '36px',
              height: '36px',
              background: '#FFF7ED',
              borderRadius: '10px',
            }}
          >
            <MapPin size={18} style={{ color: '#F97316' }} strokeWidth={2.5} />
          </div>
          <div className="min-w-0">
            <h2
              style={{
                fontSize: '15px',
                fontWeight: 800,
                color: '#1C1917',
                letterSpacing: '-0.2px',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {hotspot.name}
            </h2>
            <div className="flex items-center gap-1.5">
              <Settings2 size={11} style={{ color: '#F97316' }} />
              <span style={{ fontSize: '12px', color: '#F97316', fontWeight: 600 }}>Set your preferences</span>
            </div>
          </div>
        </div>

        <button
          className="flex items-center justify-center"
          style={{
            width: '40px',
            height: '40px',
            background: '#1C1917',
            borderRadius: '12px',
            border: 'none',
            cursor: 'pointer',
            flexShrink: 0,
          }}
        >
          <User size={18} style={{ color: 'white' }} strokeWidth={2} />
        </button>
      </div>

      {/* Content */}
      <div className="flex flex-col gap-0 px-5 pt-5 pb-28">

        {/* Date */}
        <div
          className="mb-4"
          style={{
            background: 'white',
            borderRadius: '20px',
            padding: '18px 18px',
            border: '1.5px solid #F3F2F0',
            boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
          }}
        >
          <div className="flex items-center gap-2 mb-3">
            <Calendar size={14} style={{ color: '#A8A29E' }} />
            <span style={{ fontSize: '11px', fontWeight: 700, color: '#A8A29E', letterSpacing: '0.8px', textTransform: 'uppercase' }}>
              Date
            </span>
          </div>
          <div
            className="flex items-center justify-between"
            style={{
              background: '#FFF7ED',
              borderRadius: '12px',
              padding: '12px 14px',
              border: '1.5px solid #FDDCB5',
            }}
          >
            <div className="flex items-center gap-3">
              <div
                style={{
                  width: '30px',
                  height: '30px',
                  background: '#F97316',
                  borderRadius: '8px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                }}
              >
                <Calendar size={14} color="white" strokeWidth={2.5} />
              </div>
              <span style={{ fontSize: '16px', fontWeight: 700, color: '#F97316' }}>{formattedDate}</span>
            </div>
            <button
              style={{
                background: 'none',
                border: 'none',
                fontSize: '13px',
                fontWeight: 700,
                color: '#F97316',
                cursor: 'pointer',
              }}
            >
              Change
            </button>
          </div>
        </div>

        {/* Time Range */}
        <div
          className="mb-4"
          style={{
            background: 'white',
            borderRadius: '20px',
            padding: '18px 18px',
            border: '1.5px solid #F3F2F0',
            boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
          }}
        >
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <Clock size={14} style={{ color: '#A8A29E' }} />
              <span style={{ fontSize: '11px', fontWeight: 700, color: '#A8A29E', letterSpacing: '0.8px', textTransform: 'uppercase' }}>
                Time Window
              </span>
            </div>
            <div
              className="flex items-center gap-1.5"
              style={{
                background: '#FFF7ED',
                padding: '5px 10px',
                borderRadius: '20px',
              }}
            >
              <span style={{ fontSize: '13px', fontWeight: 700, color: '#F97316' }}>
                {formatHour(timeRange[0])} – {formatHour(timeRange[1])}
              </span>
            </div>
          </div>

          <div className="flex items-center justify-between mb-3">
            <span style={{ fontSize: '12px', color: '#A8A29E', fontWeight: 500 }}>16:00</span>
            <span style={{ fontSize: '12px', color: '#A8A29E', fontWeight: 500 }}>22:00</span>
          </div>

          <Slider.Root
            className="relative flex items-center w-full"
            style={{ height: '24px' }}
            min={16}
            max={22}
            step={1}
            value={timeRange}
            onValueChange={setTimeRange}
          >
            <Slider.Track
              style={{
                background: '#F3F2F0',
                position: 'relative',
                flexGrow: 1,
                borderRadius: '9999px',
                height: '5px',
              }}
            >
              <Slider.Range
                style={{
                  position: 'absolute',
                  background: 'linear-gradient(90deg, #F97316, #FB923C)',
                  borderRadius: '9999px',
                  height: '100%',
                }}
              />
            </Slider.Track>
            {timeRange.map((_, i) => (
              <Slider.Thumb
                key={i}
                style={{
                  display: 'block',
                  width: '22px',
                  height: '22px',
                  background: 'white',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
                  borderRadius: '50%',
                  border: '2.5px solid #F97316',
                  cursor: 'pointer',
                  outline: 'none',
                }}
              />
            ))}
          </Slider.Root>
        </div>

        {/* Age Range */}
        <div
          className="mb-4"
          style={{
            background: 'white',
            borderRadius: '20px',
            padding: '18px 18px',
            border: '1.5px solid #F3F2F0',
            boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
          }}
        >
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <Users size={14} style={{ color: '#A8A29E' }} />
              <span style={{ fontSize: '11px', fontWeight: 700, color: '#A8A29E', letterSpacing: '0.8px', textTransform: 'uppercase' }}>
                Age Range
              </span>
            </div>
            <div
              style={{
                background: '#FFF7ED',
                padding: '5px 10px',
                borderRadius: '20px',
              }}
            >
              <span style={{ fontSize: '13px', fontWeight: 700, color: '#F97316' }}>
                {ageRange[0]} – {ageRange[1]} yrs
              </span>
            </div>
          </div>

          <div className="flex items-center justify-between mb-3">
            <span style={{ fontSize: '12px', color: '#A8A29E', fontWeight: 500 }}>18</span>
            <span style={{ fontSize: '12px', color: '#A8A29E', fontWeight: 500 }}>40</span>
          </div>

          <Slider.Root
            className="relative flex items-center w-full"
            style={{ height: '24px' }}
            min={18}
            max={40}
            step={1}
            value={ageRange}
            onValueChange={setAgeRange}
          >
            <Slider.Track
              style={{
                background: '#F3F2F0',
                position: 'relative',
                flexGrow: 1,
                borderRadius: '9999px',
                height: '5px',
              }}
            >
              <Slider.Range
                style={{
                  position: 'absolute',
                  background: 'linear-gradient(90deg, #F97316, #FB923C)',
                  borderRadius: '9999px',
                  height: '100%',
                }}
              />
            </Slider.Track>
            {ageRange.map((_, i) => (
              <Slider.Thumb
                key={i}
                style={{
                  display: 'block',
                  width: '22px',
                  height: '22px',
                  background: 'white',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
                  borderRadius: '50%',
                  border: '2.5px solid #F97316',
                  cursor: 'pointer',
                  outline: 'none',
                }}
              />
            ))}
          </Slider.Root>
        </div>

        {/* Gender */}
        <div
          className="mb-4"
          style={{
            background: 'white',
            borderRadius: '20px',
            padding: '18px 18px',
            border: '1.5px solid #F3F2F0',
            boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
          }}
        >
          <div className="flex items-center gap-2 mb-4">
            <Users size={14} style={{ color: '#A8A29E' }} />
            <span style={{ fontSize: '11px', fontWeight: 700, color: '#A8A29E', letterSpacing: '0.8px', textTransform: 'uppercase' }}>
              Preferred Gender
            </span>
          </div>
          <div className="flex gap-2.5">
            {(['Male', 'Female', 'Any'] as Gender[]).map((g) => (
              <button
                key={g}
                onClick={() => setGender(g)}
                style={{
                  flex: 1,
                  padding: '11px 0',
                  borderRadius: '12px',
                  border: gender === g ? 'none' : '1.5px solid #E7E5E4',
                  background: gender === g
                    ? 'linear-gradient(135deg, #F97316, #FB923C)'
                    : 'white',
                  cursor: 'pointer',
                  fontSize: '14px',
                  fontWeight: gender === g ? 700 : 500,
                  color: gender === g ? 'white' : '#78716C',
                  boxShadow: gender === g ? '0 4px 12px rgba(249,115,22,0.28)' : 'none',
                  transition: 'all 0.2s',
                  fontFamily: 'Inter, sans-serif',
                }}
              >
                {g}
              </button>
            ))}
          </div>
        </div>

        {/* Private Walk */}
        <div
          className="mb-6"
          style={{
            background: 'white',
            borderRadius: '20px',
            padding: '18px 18px',
            border: '1.5px solid #F3F2F0',
            boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
          }}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-start gap-3">
              <div
                className="flex items-center justify-center"
                style={{
                  width: '38px',
                  height: '38px',
                  background: isPrivate ? '#FFF7ED' : '#F5F5F4',
                  borderRadius: '12px',
                  flexShrink: 0,
                }}
              >
                <Lock size={18} style={{ color: isPrivate ? '#F97316' : '#A8A29E' }} strokeWidth={2} />
              </div>
              <div>
                <span style={{ fontSize: '15px', fontWeight: 700, color: '#1C1917', display: 'block' }}>
                  Private Walk
                </span>
                <span style={{ fontSize: '13px', color: '#A8A29E', fontWeight: 400 }}>
                  Invite a friend directly
                </span>
              </div>
            </div>
            <Switch.Root
              checked={isPrivate}
              onCheckedChange={setIsPrivate}
              style={{
                width: '48px',
                height: '28px',
                background: isPrivate ? '#F97316' : '#D6D3D1',
                borderRadius: '14px',
                position: 'relative',
                border: 'none',
                cursor: 'pointer',
                transition: 'background 0.2s',
                flexShrink: 0,
              }}
            >
              <Switch.Thumb
                style={{
                  display: 'block',
                  width: '22px',
                  height: '22px',
                  background: 'white',
                  borderRadius: '11px',
                  boxShadow: '0 2px 4px rgba(0,0,0,0.15)',
                  transform: `translateX(${isPrivate ? '23px' : '3px'})`,
                  transition: 'transform 0.2s',
                }}
              />
            </Switch.Root>
          </div>
        </div>
      </div>

      {/* Sticky bottom button */}
      <div
        className="absolute bottom-0 left-0 right-0"
        style={{
          padding: '12px 20px 28px',
          background: 'linear-gradient(to top, #FEF9F5 80%, transparent)',
        }}
      >
        <button
          onClick={handleFindMatch}
          disabled={finding}
          className="w-full flex items-center justify-center gap-2.5"
          style={{
            background: finding
              ? '#FDBA74'
              : 'linear-gradient(135deg, #F97316 0%, #EA6B0E 100%)',
            border: 'none',
            borderRadius: '18px',
            padding: '18px',
            cursor: finding ? 'not-allowed' : 'pointer',
            boxShadow: finding ? 'none' : '0 8px 24px rgba(249,115,22,0.38)',
            transition: 'all 0.2s',
          }}
        >
          {finding ? (
            <>
              <svg
                className="animate-spin"
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
              >
                <circle cx="12" cy="12" r="10" stroke="white" strokeWidth="3" strokeOpacity="0.3" />
                <path d="M12 2a10 10 0 0 1 10 10" stroke="white" strokeWidth="3" strokeLinecap="round" />
              </svg>
              <span style={{ fontSize: '16px', fontWeight: 700, color: 'white' }}>Finding your mate...</span>
            </>
          ) : (
            <>
              <Sparkles size={20} color="white" strokeWidth={2.5} />
              <span style={{ fontSize: '16px', fontWeight: 800, color: 'white', letterSpacing: '-0.2px' }}>
                Find Match
              </span>
            </>
          )}
        </button>
      </div>
    </div>
  );
}