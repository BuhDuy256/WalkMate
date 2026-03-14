package com.walkmate.infrastructure.repository;

import com.walkmate.domain.intent.IntentRepository;
import com.walkmate.domain.intent.IntentStatus;
import com.walkmate.domain.intent.WalkIntent;
import com.walkmate.domain.valueobject.LocationSnapshot;
import com.walkmate.domain.valueobject.TimeWindow;
import com.walkmate.infrastructure.repository.entity.WalkIntentJpaEntity;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaIntentRepository implements IntentRepository {

  private final WalkIntentSpringDataRepository springDataRepository;
  private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

  @Override
  public WalkIntent save(WalkIntent intent) {
    WalkIntentJpaEntity entity = toEntity(intent);
    WalkIntentJpaEntity saved = springDataRepository.save(entity);
    return toDomain(saved);
  }

  @Override
  public Optional<WalkIntent> findById(UUID id) {
    return springDataRepository.findById(id).map(this::toDomain);
  }

  @Override
  public List<WalkIntent> findByUserIdAndStatus(UUID userId, IntentStatus status) {
    return springDataRepository.findByUserIdAndStatus(userId, status)
        .stream().map(this::toDomain).toList();
  }

  @Override
  public List<WalkIntent> findExpiredOpenIntentsForUpdate(Instant now, int limit) {
    return springDataRepository.findExpiredOpenIntentsForUpdate(now, limit)
        .stream().map(this::toDomain).toList();
  }

  private WalkIntentJpaEntity toEntity(WalkIntent domain) {
    WalkIntentJpaEntity entity = new WalkIntentJpaEntity();
    entity.setIntentId(domain.getIntentId());
    entity.setUserId(domain.getUserId());

    Point point = geometryFactory.createPoint(new Coordinate(
        domain.getLocation().lng(), domain.getLocation().lat()
    ));
    entity.setLocation(point);
    entity.setLocationLat(domain.getLocation().lat());
    entity.setLocationLng(domain.getLocation().lng());

    entity.setTimeWindowStart(domain.getTimeWindow().start());
    entity.setTimeWindowEnd(domain.getTimeWindow().end());
    entity.setPurpose(domain.getPurpose());
    entity.setMatchingConstraints(domain.getMatchingConstraints());
    entity.setStatus(domain.getStatus());
    entity.setCreatedAt(domain.getCreatedAt());
    entity.setExpiresAt(domain.getExpiresAt());
    entity.setVersion(domain.getVersion());
    return entity;
  }

  private WalkIntent toDomain(WalkIntentJpaEntity entity) {
    return new WalkIntent(
        entity.getIntentId(),
        entity.getUserId(),
        new LocationSnapshot(entity.getLocationLat(), entity.getLocationLng()),
        new TimeWindow(entity.getTimeWindowStart(), entity.getTimeWindowEnd()),
        entity.getPurpose(),
        entity.getMatchingConstraints(),
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getExpiresAt(),
        entity.getVersion()
    );
  }
}
