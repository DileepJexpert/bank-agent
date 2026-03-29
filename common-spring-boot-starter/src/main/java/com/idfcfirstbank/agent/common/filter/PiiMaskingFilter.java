package com.idfcfirstbank.agent.common.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import com.idfcfirstbank.agent.common.util.MaskingUtils;
import org.slf4j.Marker;

/**
 * Logback {@link TurboFilter} that intercepts every logging call and masks PII
 * (Aadhaar, PAN, card numbers, phone numbers, email addresses) before the
 * message reaches any appender.
 *
 * <p>Register in {@code logback-spring.xml}:
 * <pre>{@code
 * <turboFilter class="com.idfcfirstbank.agent.common.filter.PiiMaskingFilter"/>
 * }</pre>
 */
public class PiiMaskingFilter extends TurboFilter {

    @Override
    public FilterReply decide(Marker marker,
                              Logger logger,
                              Level level,
                              String format,
                              Object[] params,
                              Throwable throwable) {

        if (params != null && params.length > 0) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof String s) {
                    params[i] = MaskingUtils.maskAll(s);
                }
            }
        }

        // NEUTRAL lets the rest of the filter chain and level check proceed normally
        return FilterReply.NEUTRAL;
    }
}
