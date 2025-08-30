package com.demo.credit.controller.dto;

import java.util.List;

public class RawSignalsRequest {
    public List<Sms>      sms;
    public List<Contact>  contacts;
    public List<Email>    emails;
    public List<Ecom>     ecom;
    public List<WebEvt>   web;

    public static class Sms {
        public String text;
        public Long ts; // epoch ms
    }
    public static class Contact {
        public String name;
        public String phone;
    }
    public static class Email {
        public String subject;
        public boolean overdueNotice; // true nếu là mail nhắc nợ/quá hạn
        public Long ts;
    }
    public static class Ecom {
        public String category; // ví dụ: "fashion", "electronics", ...
        public Double amount;   // số tiền chi tiêu
        public Long ts;
    }
    public static class WebEvt {
        public String url;
        public Long ts;
    }
}
