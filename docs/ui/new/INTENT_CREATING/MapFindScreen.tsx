import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router';
import { ChevronLeft, User, Search, SlidersHorizontal, Footprints, ChevronRight } from 'lucide-react';
import { BottomNav } from '../layout/BottomNav';

const HOTSPOTS = [
  { id: 1, name: 'Le Van Tam Park', lat: 10.7891, lng: 106.6891 },
  { id: 2, name: 'Independence Palace Square', lat: 10.7769, lng: 106.6956 },
  { id: 3, name: 'Tao Dan Park', lat: 10.7750, lng: 106.6929 },
  { id: 4, name: 'Nguyen Hue Walking Street', lat: 10.7730, lng: 106.7043 },
];

export function MapFindScreen() {
  const navigate = useNavigate();
  const mapRef = useRef<HTMLDivElement>(null);
  const leafletMapRef = useRef<any>(null);

  useEffect(() => {
    let L: any;
    let map: any;

    const initMap = async () => {
      try {
        const leaflet = await import('leaflet');
        L = leaflet.default || leaflet;

        if (!mapRef.current || leafletMapRef.current) return;

        map = L.map(mapRef.current, {
          center: [10.7769, 106.7009],
          zoom: 14,
          zoomControl: false,
          attributionControl: false,
        });

        leafletMapRef.current = map;

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          attribution: '© OpenStreetMap',
        }).addTo(map);

        // Add all markers
        HOTSPOTS.forEach((hotspot) => {
          const markerHtml = `
            <div class="wm-marker-label">
              <div class="pin-dot"></div>
              <span>${hotspot.name}</span>
            </div>
          `;

          const icon = L.divIcon({
            html: markerHtml,
            className: '',
            iconAnchor: [80, 18],
            iconSize: [200, 36],
          });

          L.marker([hotspot.lat, hotspot.lng], { icon })
            .addTo(map)
            .on('click', () => {
              navigate('/hotspot', { state: { hotspot } });
            });
        });

        // Ensure map renders correctly
        setTimeout(() => map.invalidateSize(), 100);
      } catch (e) {
        console.error('Leaflet error:', e);
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
      {/* Map fills entire screen */}
      <div className="relative flex-1" style={{ overflow: 'hidden', minHeight: 0 }}>
        <div ref={mapRef} style={{ position: 'absolute', inset: 0 }} />

        {/* Top controls overlay */}
        <div
          className="absolute top-0 left-0 right-0 flex items-center justify-between"
          style={{ padding: '52px 16px 12px', zIndex: 20 }}
        >
          <button
            onClick={() => navigate('/home')}
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

          {/* Logo in center */}
          <div
            className="flex items-center gap-2"
            style={{
              background: 'white',
              borderRadius: '14px',
              padding: '8px 14px',
              boxShadow: '0 4px 16px rgba(0,0,0,0.12)',
            }}
          >
            <div
              style={{
                width: '28px',
                height: '28px',
                background: 'linear-gradient(135deg, #F97316, #FB923C)',
                borderRadius: '8px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Footprints size={15} color="white" strokeWidth={2} />
            </div>
            <span style={{ fontSize: '14px', fontWeight: 700, color: '#1C1917' }}>WalkMate</span>
          </div>

          {/* Avatar */}
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
          padding: '12px 20px 0',
          boxShadow: '0 -8px 32px rgba(0,0,0,0.10)',
          position: 'relative',
          zIndex: 20,
          paddingBottom: '80px',
        }}
      >
        {/* Drag handle */}
        <div className="drag-handle mb-4" />

        <div className="flex items-start justify-between mb-1">
          <div>
            <h2 style={{ fontSize: '22px', fontWeight: 800, color: '#1C1917', letterSpacing: '-0.3px' }}>
              Ready to walk? 🚶
            </h2>
            <p style={{ fontSize: '14px', color: '#A8A29E', fontWeight: 400, marginTop: '3px' }}>
              Pick a hotspot to get started
            </p>
          </div>
        </div>

        {/* Search bar */}
        <div
          className="flex items-center gap-3 mt-4 mb-4"
          style={{
            background: '#F5F5F4',
            borderRadius: '14px',
            padding: '12px 14px',
          }}
        >
          <Search size={17} style={{ color: '#A8A29E', flexShrink: 0 }} strokeWidth={2} />
          <input
            placeholder="Search hotspots..."
            style={{
              border: 'none',
              outline: 'none',
              background: 'transparent',
              flex: 1,
              fontSize: '14px',
              color: '#1C1917',
              fontFamily: 'Inter, sans-serif',
            }}
          />
          <div
            className="flex items-center justify-center"
            style={{
              width: '32px',
              height: '32px',
              background: '#F97316',
              borderRadius: '10px',
            }}
          >
            <SlidersHorizontal size={15} color="white" strokeWidth={2.5} />
          </div>
        </div>

        {/* Hotspot chips */}
        <div className="flex gap-2.5 overflow-x-auto pb-2" style={{ scrollbarWidth: 'none' }}>
          {HOTSPOTS.map((hotspot) => (
            <button
              key={hotspot.id}
              onClick={() => navigate('/hotspot', { state: { hotspot } })}
              className="flex items-center gap-2 flex-shrink-0"
              style={{
                background: 'white',
                border: '1.5px solid #E7E5E4',
                borderRadius: '12px',
                padding: '9px 14px',
                cursor: 'pointer',
              }}
            >
              <div
                style={{
                  width: '8px',
                  height: '8px',
                  background: '#EF4444',
                  borderRadius: '50%',
                  flexShrink: 0,
                }}
              />
              <span style={{ fontSize: '13px', fontWeight: 600, color: '#44403C', whiteSpace: 'nowrap' }}>
                {hotspot.name}
              </span>
              <ChevronRight size={13} style={{ color: '#A8A29E' }} />
            </button>
          ))}
        </div>
      </div>

      <BottomNav />
    </div>
  );
}