package br.com.vega.armazem.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Relógio controlável para testes: permite simular "amanhã" e horários específicos. */
public class RelogioAjustavel extends Clock {

    private final ZoneId zona;
    private volatile Instant instante;

    public RelogioAjustavel(LocalDateTime dataHoraLocal, ZoneId zona) {
        this.zona = zona;
        this.instante = dataHoraLocal.atZone(zona).toInstant();
    }

    public void ajustar(LocalDateTime dataHoraLocal) {
        this.instante = dataHoraLocal.atZone(zona).toInstant();
    }

    public void avancar(Duration duracao) {
        this.instante = instante.plus(duracao);
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId outraZona) {
        return Clock.fixed(instante, outraZona);
    }

    @Override
    public Instant instant() {
        return instante;
    }
}
