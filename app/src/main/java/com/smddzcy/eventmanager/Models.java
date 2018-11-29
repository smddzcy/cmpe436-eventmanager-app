package com.smddzcy.eventmanager;

import java.util.List;
import java.util.UUID;

public class Models {
    static class Incident {
        UUID id;
        String reportedBy;
        String location;
        String incident;
        String date;

        public Incident() {
        }
    }

    static class User {
        UUID id;
        String username;
        String password;

        public User() {
        }
    }

    static class SuccessMessage {
        String type;
        List<Incident> payload;

        public SuccessMessage() {
        }
    }

    static class FailMessage {
        String type;
        String payload; // reason

        public FailMessage() {
        }
    }
}
