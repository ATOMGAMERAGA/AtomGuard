package com.atomguard.limbo.check;

/**
 * KeepAlive yanıt kontrolü.
 *
 * <p>Sunucu her 5 tick'te bir KeepAlive paketi gönderir. Gerçek client'lar
 * bunu yanıtlar; bağlantı-only botlar genellikle yanıtlamaz.
 *
 * <p>Bu check zayıf bir sinyal; diğer check'lerle birlikte değerlendirilen
 * tamamlayıcı bir göstergedir.
 */
public class KeepAliveCheck {

    private int sent   = 0;
    private int answered = 0;

    public void onKeepAliveSent() {
        sent++;
    }

    public void onKeepAliveResponse() {
        answered++;
    }

    /**
     * KeepAlive yanıt oranı yeterli mi?
     * En az 1 gönderilmiş ve yanıtlanmış olmalı.
     */
    public boolean isSatisfied() {
        if (sent == 0) return true; // Henüz gönderilmedi → belirsiz, fail ettirme
        return answered >= 1;
    }

    public int getSent()     { return sent; }
    public int getAnswered() { return answered; }
}
