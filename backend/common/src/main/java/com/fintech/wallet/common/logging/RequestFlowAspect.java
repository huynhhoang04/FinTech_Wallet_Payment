package com.fintech.wallet.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Traces which controller, service, repository and helper beans take part in a request, one
 * indented line per call, so the flow can be read straight out of the log:
 *
 * <pre>
 * POST /api/v1/auth/login  -> AuthController.login
 *   AuthService.login
 *     UserRepository.findByPhone
 *     JwtUtils.generate
 * &lt;- POST /api/v1/auth/login  -> AuthController.login [178 ms, HTTP 200]
 * </pre>
 *
 * Off outside dev ({@code app.logging.request-flow}).
 *
 * <p>The pointcut is deliberately targeted instead of "every method under com.fintech.wallet". A
 * catch-all {@code execution(* com.fintech.wallet..*.*(..))} also matches the servlet
 * {@code RequestIdFilter}, so Spring wraps it in a CGLIB proxy that cannot override the final
 * {@code GenericFilterBean.init(FilterConfig)}; the filter's logger is then never initialised and
 * Tomcat fails to start with an NPE. The same reason explains the two gaps below.
 *
 * <p>Not traced: framework beans such as {@code BCryptPasswordEncoder}, which live outside our
 * packages (so {@code PasswordEncoder.matches} does not appear), and static helpers such as
 * {@code LedgerEntryFactory}, which are not beans at all.
 */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(name = RequestFlowAspect.ENABLED_PROPERTY, havingValue = "true")
public class RequestFlowAspect {

	public static final String ENABLED_PROPERTY = "app.logging.request-flow";

	private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
	private static final String INDENT = "  ";

	public RequestFlowAspect() {
		log.info("Request-flow logging is ON ({}=true)", ENABLED_PROPERTY);
	}

	@Around("within(@org.springframework.web.bind.annotation.RestController *)"
			+ " || within(@org.springframework.stereotype.Service *)"
			+ " || within(com.fintech.wallet..repository..*)"
			+ " || execution(* com.fintech.wallet.common.security.JwtUtils.*(..))")
	public Object logRequestFlow(ProceedingJoinPoint joinPoint) throws Throwable {
		int depth = DEPTH.get();
		String indent = INDENT.repeat(depth);
		String frame = describe(joinPoint);
		boolean outermost = depth == 0;

		log.info("{}{}", indent, frame);
		DEPTH.set(depth + 1);
		long start = System.nanoTime();
		try {
			Object result = joinPoint.proceed();
			if (outermost) {
				log.info("{}<- {} [{} ms, HTTP {}]", indent, frame, elapsedMillis(start), httpStatus());
			}
			return result;
		} catch (Throwable throwable) {
			log.info("{}<- {} [{} ms] threw {}{}", indent, frame, elapsedMillis(start),
					throwable.getClass().getSimpleName(), httpStatusSuffix());
			throw throwable;
		} finally {
			DEPTH.set(depth);
		}
	}

	private static String describe(ProceedingJoinPoint joinPoint) {
		String target = joinPoint.getSignature().getDeclaringType().getSimpleName()
				+ "." + joinPoint.getSignature().getName();
		String api = currentApi(joinPoint);
		return api == null ? target : api + "  -> " + target;
	}

	private static String currentApi(ProceedingJoinPoint joinPoint) {
		if (!joinPoint.getSignature().getDeclaringType().isAnnotationPresent(RestController.class)) {
			return null;
		}
		HttpServletRequest request = currentRequest();
		return request == null ? null : request.getMethod() + " " + request.getRequestURI();
	}

	private static String httpStatus() {
		ServletRequestAttributes attributes = requestAttributes();
		if (attributes == null || attributes.getResponse() == null) {
			return "-";
		}
		return String.valueOf(attributes.getResponse().getStatus());
	}

	private static String httpStatusSuffix() {
		String status = httpStatus();
		return "-".equals(status) ? "" : " (HTTP " + status + ")";
	}

	private static HttpServletRequest currentRequest() {
		ServletRequestAttributes attributes = requestAttributes();
		return attributes == null ? null : attributes.getRequest();
	}

	private static ServletRequestAttributes requestAttributes() {
		return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
				? attributes
				: null;
	}

	private static long elapsedMillis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}
}
