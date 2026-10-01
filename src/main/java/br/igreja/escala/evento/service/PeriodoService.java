package br.igreja.escala.evento.service;

import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.repository.PeriodoRepository;
import java.time.YearMonth;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Os meses de cada ministério. Serviço público: a disponibilidade e a escala partem do período. */
@Service
public class PeriodoService {

    private final PeriodoRepository periodos;

    PeriodoService(PeriodoRepository periodos) {
        this.periodos = periodos;
    }

    @Transactional(readOnly = true)
    public Optional<Periodo> doMes(Long ministerioId, YearMonth mes) {
        return periodos.findByMinisterioIdAndAnoAndMes(ministerioId, mes.getYear(), mes.getMonthValue());
    }

    /** O período do mês, criado agora se o mês ainda não tinha nenhum evento. */
    @Transactional
    public Periodo obterOuCriar(Long ministerioId, YearMonth mes) {
        return doMes(ministerioId, mes).orElseGet(() -> periodos.save(new Periodo(ministerioId, mes)));
    }
}
