package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.dto.DecisionRunReq;
import lombok.Builder;
import lombok.Value;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 一次决策运行共享的时点上下文。
 */
@Value
@Builder
public class DecisionContext {

    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    LocalDate actionDate;
    LocalDateTime asOfTime;
    DecisionMode mode;
    String runNo;
    DecisionDataPolicy dataPolicy;

    public static DecisionContext from(DecisionRunReq request) {
        return from(request, Clock.systemDefaultZone());
    }

    public static DecisionContext from(DecisionRunReq request, Clock clock) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate requestedDate = request == null ? null : request.getDate();
        DecisionMode requestedMode = request == null ? null : request.getMode();
        DecisionMode mode = resolveMode(requestedMode, requestedDate, now.toLocalDate());

        if (mode == DecisionMode.REPLAY && requestedMode == DecisionMode.REPLAY && requestedDate == null) {
            throw new IllegalArgumentException("REPLAY 模式必须指定决策日");
        }

        LocalDate actionDate = requestedDate == null ? now.toLocalDate() : requestedDate;
        validateActionDate(mode, actionDate, now.toLocalDate());
        LocalDateTime requestedCutoff = request == null ? null : request.getAsOfTime();
        LocalDateTime asOfTime = resolveAsOfTime(mode, actionDate, requestedCutoff, now);

        return DecisionContext.builder()
                .actionDate(actionDate)
                .asOfTime(asOfTime)
                .mode(mode)
                .dataPolicy(mode == DecisionMode.REPLAY
                        ? DecisionDataPolicy.POINT_IN_TIME
                        : DecisionDataPolicy.LATEST_AVAILABLE)
                .build();
    }

    private static DecisionMode resolveMode(DecisionMode requestedMode,
                                            LocalDate requestedDate,
                                            LocalDate currentDate) {
        if (requestedMode != null) {
            return requestedMode;
        }
        if (requestedDate != null && requestedDate.isBefore(currentDate)) {
            return DecisionMode.REPLAY;
        }
        return DecisionMode.LIVE;
    }

    private static LocalDateTime resolveAsOfTime(DecisionMode mode,
                                                 LocalDate actionDate,
                                                 LocalDateTime requestedCutoff,
                                                 LocalDateTime now) {
        if (mode != DecisionMode.REPLAY) {
            if (requestedCutoff != null && (!requestedCutoff.toLocalDate().equals(actionDate)
                    || requestedCutoff.isAfter(now))) {
                throw new IllegalArgumentException("LIVE/SHADOW 截止时间必须是当日且不能晚于当前时间");
            }
            return requestedCutoff == null ? now : requestedCutoff;
        }
        if (actionDate.isAfter(now.toLocalDate())) {
            throw new IllegalArgumentException("REPLAY 决策日不能晚于当前日期");
        }
        if (requestedCutoff != null && !requestedCutoff.toLocalDate().equals(actionDate)) {
            throw new IllegalArgumentException("REPLAY 截止时间必须属于决策日");
        }
        if (requestedCutoff != null && !requestedCutoff.toLocalTime().equals(END_OF_DAY)) {
            throw new IllegalArgumentException("当前仅支持日终 REPLAY");
        }
        return requestedCutoff == null
                ? LocalDateTime.of(actionDate, END_OF_DAY)
                : requestedCutoff;
    }

    private static void validateActionDate(DecisionMode mode, LocalDate actionDate, LocalDate currentDate) {
        if (actionDate.isAfter(currentDate)) {
            throw new IllegalArgumentException("决策日不能晚于当前日期");
        }
        if (mode != DecisionMode.REPLAY && !actionDate.equals(currentDate)) {
            throw new IllegalArgumentException("LIVE/SHADOW 只能运行当前日期");
        }
    }
}
