package com.coachsim.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MatchEventPublisher {

    private final SimpMessagingTemplate template;

    public MatchEventPublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void publishMatchEvent(Long matchId, Map<String, Object> payload) {
        template.convertAndSend("/topic/match." + matchId, payload);
    }

    public void publishDecisionWindow(Long matchId, Map<String, Object> payload) {
        template.convertAndSend("/topic/match." + matchId + ".windows", payload);
    }

    /** Per-user reveal: fan's score + breakdown after captain move resolves. */
    public void publishDecisionResult(String userIdAsName, Map<String, Object> payload) {
        template.convertAndSendToUser(userIdAsName, "/queue/decision-result", payload);
    }
}
