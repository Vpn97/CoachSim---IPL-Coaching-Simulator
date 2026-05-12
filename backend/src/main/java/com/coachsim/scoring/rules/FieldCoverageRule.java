package com.coachsim.scoring.rules;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Only applies to FIELD_SET decisions. Awards 0–20 based on what fraction of the
 * batter's typical scoring zones the field covers.
 *
 * Without per-batter wagon wheels, we approximate with the eight cricket-ground
 * zones (covers, point, third-man, fine-leg, square-leg, mid-wicket, mid-on, mid-off).
 * Captain payload may carry "batterTopZones" : list of zone names — if present we score
 * against those, otherwise we use a uniform-coverage baseline.
 */
@Component
public class FieldCoverageRule implements Rule {

    private static final List<String> ALL_ZONES = List.of(
            "COVERS", "POINT", "THIRD_MAN", "FINE_LEG",
            "SQUARE_LEG", "MID_WICKET", "MID_ON", "MID_OFF");

    @Override
    public String id() { return "field_coverage"; }

    @Override
    @SuppressWarnings("unchecked")
    public Verdict apply(Context ctx) {
        if (ctx.window().getTargetType() != com.coachsim.decision.DecisionWindow.TargetType.FIELD_SET) {
            return new Verdict(id(), 0, 0, "n/a for BOWLING_CHANGE");
        }

        Map<String, Object> fan = ctx.fanDecision().getPayload();
        Map<String, Object> capt = ctx.captainMove().getPayload();

        Set<String> coveredZones = new HashSet<>();
        Object positions = fan.get("positions");
        if (positions instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("zone") instanceof String z) {
                    coveredZones.add(z.toUpperCase());
                }
            }
        }

        List<String> targetZones;
        Object topZones = capt.get("batterTopZones");
        if (topZones instanceof List<?> tz && !tz.isEmpty()) {
            targetZones = ((List<Object>) tz).stream().map(Object::toString).map(String::toUpperCase).toList();
        } else {
            targetZones = ALL_ZONES;
        }

        long hits = targetZones.stream().filter(coveredZones::contains).count();
        double pct = targetZones.isEmpty() ? 0 : (double) hits / targetZones.size();
        int pts = (int) Math.round(pct * 20);
        return new Verdict(id(), pts, 20,
                String.format("Covers %d/%d high-scoring zones (%d%%)", hits, targetZones.size(), Math.round(pct * 100)));
    }
}
