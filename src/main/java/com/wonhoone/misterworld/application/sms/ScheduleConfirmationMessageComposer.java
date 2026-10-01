package com.wonhoone.misterworld.application.sms;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class ScheduleConfirmationMessageComposer {
    public String compose(String productName, LocalDate startDate, LocalDate endDate) {
        return "[미스터월드] " + productName + " 여행의 출발이 확정되었습니다. 일정: " + startDate + " ~ " + endDate;
    }
}
