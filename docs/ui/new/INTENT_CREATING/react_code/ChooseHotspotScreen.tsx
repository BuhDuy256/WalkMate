import { useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router';
import { ChevronLeft, User, MapPin, Calendar, Clock, ChevronRight, Settings2 } from 'lucide-react';

const DEFAULT_HOTSPOT = {
  id: 2,
  name: 'Independence Palace Square',
  lat: 10.7769,
  lng: 106.6956,
};

const HOTSPOTS = [
  { id: 1, name: 'Le Van Tam Park', lat: 10.7891, lng: 106.6891 },
  { id: 2, name: 'Independence Palace Square', lat: 10.7769, lng: 106.6956 },
  { id: 3, name: 'Tao Dan Park', lat: 10.7750, lng: 106.6929 },
  { id: 4, name: 'Nguyen Hue Walking Street', lat: 10.7730, lng: 106.7043 },
];

export function ChooseHotspotScreen() {
  const navigate = useNavigate();
  const location = useLocation();
  const mapRef = useRef<HTMLDivElement>(null);
  const leafletMapRef = useRef<any>(null);
  const hotspot = (location.state as any)?.hotspot || DEFAULT_HOTSPOT;

  const today = new Date();
  const formattedDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;

  useEffect(() => {
    let map: any;

    const initMap = async () => {
      try {
        const leaflet = await import('leaflet');
        const L = leaflet.default || leaflet;

        if (!mapRef.current || leafletMapRef.current) return;

        map = L.map(mapRef.current, {
          center: [hotspot.lat, hotspot.lng],
          zoom: 14,
          zoomControl: false,
          attributionControl: false,
        });

        leafletMapRef.current = map;

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          attribution: '© OpenStreetMap',
        }).addTo(map);

        // Add all markers
        HOTSPOTS.forEach((h) => {
          const isSelected = h.id === hotspot.id;
          const markerHtml = `
            <div class="wm-marker-label ${isSelected ? 'selected' : ''}">
              <div class="pin-dot"></div>
              <span>${h.name}</span>
            </div>
          `;
          const icon = L.divIcon({
            html: markerHtml,
            className: '',
            iconAnchor: [80, 18],
            iconSize: [200, 36],
          });
          L.marker([h.lat, h.lng], { icon }).addTo(map);
        });

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

  return (
    <div className="w-full h-full flex flex-col" style={{ background: '#FEF9F5' }}>
      {/* Map */}
      <div className="relative" style={{ flex: '1 1 0', overflow: 'hidden', minHeight: '240px' }}>
        <div ref={mapRef} style={{ position: 'absolute', inset: 0 }} />

        {/* Top controls */}
        <div
          className="absolute top-0 left-0 right-0 flex items-center justify-between"
          style={{ padding: '52px 16px 12px', zIndex: 20 }}
        >
          <button
            onClick={() => navigate('/find')}
            className="flex items-center justify-center"
            style={{
              width: '44px',
              height: '44px',
              background: 'white',
              borderRadius: '14px',
              border: 'none',
              boxShadow: '0 4px 16px rgba(0,0,0,0.12)',
              cursor: 'pointer',
            }}
          >
            <ChevronLeft size={22} style={{ color: '#1C1917' }} strokeWidth={2.5} />
          </button>

          <button
            className="flex items-center justify-center"
            style={{
              width: '44px',
              height: '44px',
              background: '#1C1917',
              borderRadius: '14px',
              border: 'none',
              boxShadow: '0 4px 16px rgba(0,0,0,0.16)',
              cursor: 'pointer',
            }}
          >
            <User size={20} style={{ color: 'white' }} strokeWidth={2} />
          </button>
        </div>
      </div>

      {/* Bottom Sheet */}
      <div
        style={{
          background: 'white',
          borderRadius: '28px 28px 0 0',
          padding: '12px 20px 20px',
          boxShadow: '0 -8px 32px rgba(0,0,0,0.10)',
          flex: '0 0 auto',
          overflowY: 'auto',
          maxHeight: '55%',
        }}
      >
        {/* Drag handle */}
        <div className="drag-handle mb-5" />

        {/* Location header */}
        <div className="flex items-start justify-between mb-5">
          <div className="flex items-start gap-3">
            <div
              className="flex items-center justify-center flex-shrink-0"
              style={{
                width: '44px',
                height: '44px',
                background: '#FFF7ED',
                borderRadius: '14px',
              }}
            >
              <MapPin size={22} style={{ color: '#F97316' }} strokeWidth={2} />
            </div>
            <div>
              <h2
                style={{
                  fontSize: '18px',
                  fontWeight: 800,
                  color: '#1C1917',
                  letterSpacing: '-0.3px',
                  lineHeight: 1.2,
                }}
              >
                {hotspot.name}
              </h2>
              <button
                className="flex items-center gap-1 mt-1"
                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}
              >
                <Settings2 size={13} style={{ color: '#F97316' }} strokeWidth={2.5} />
                <span style={{ fontSize: '13px', color: '#F97316', fontWeight: 600 }}>
                  Set your preferences
                </span>
              </button>
            </div>
          </div>
        </div>

        {/* Date section */}
        <div className="mb-4">
          <div className="flex items-center gap-2 mb-2.5">
            <Calendar size={14} style={{ color: '#A8A29E' }} />
            <span style={{ fontSize: '12px', fontWeight: 700, color: '#A8A29E', letterSpacing: '0.5px', textTransform: 'uppercase' }}>
              Date
            </span>
          </div>
          <div
            className="flex items-center justify-between"
            style={{
              background: '#FFF7ED',
              borderRadius: '14px',
              padding: '13px 16px',
              border: '1.5px solid #FDDCB5',
            }}
          >
            <div className="flex items-center gap-3">
              <div
                className="flex items-center justify-center"
                style={{
                  width: '32px',
                  height: '32px',
                  background: '#F97316',
                  borderRadius: '8px',
                }}
              >
                <Calendar size={15} color="white" strokeWidth={2.5} />
              </div>
              <span style={{ fontSize: '16px', fontWeight: 700, color: '#F97316' }}>
                {formattedDate}
              </span>
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

        {/* Time section (preview) */}
        <div className="mb-5">
          <div className="flex items-center gap-2 mb-2.5">
            <Clock size={14} style={{ color: '#A8A29E' }} />
            <span style={{ fontSize: '12px', fontWeight: 700, color: '#A8A29E', letterSpacing: '0.5px', textTransform: 'uppercase' }}>
              Time
            </span>
          </div>
          <div
            className="flex items-center gap-2"
            style={{
              background: '#F5F5F4',
              borderRadius: '14px',
              padding: '13px 16px',
            }}
          >
            <span style={{ fontSize: '15px', fontWeight: 600, color: '#78716C' }}>16:00</span>
            <div style={{ flex: 1, height: '2px', background: '#E7E5E4', borderRadius: '1px', position: 'relative' }}>
              <div style={{ position: 'absolute', left: '40%', right: '20%', top: 0, height: '100%', background: '#F97316', borderRadius: '1px' }} />
            </div>
            <span style={{ fontSize: '15px', fontWeight: 600, color: '#78716C' }}>22:00</span>
          </div>
        </div>

        {/* Continue button */}
        <button
          onClick={() => navigate('/create-intent', { state: { hotspot } })}
          className="w-full flex items-center justify-center gap-2"
          style={{
            background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
            border: 'none',
            borderRadius: '16px',
            padding: '17px',
            cursor: 'pointer',
            boxShadow: '0 6px 20px rgba(249,115,22,0.32)',
          }}
        >
          <span style={{ fontSize: '16px', fontWeight: 700, color: 'white' }}>
            Set Preferences
          </span>
          <ChevronRight size={18} color="white" strokeWidth={2.5} />
        </button>
      </div>
    </div>
  );
}