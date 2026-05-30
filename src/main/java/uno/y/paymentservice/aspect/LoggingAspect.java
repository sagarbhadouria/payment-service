package uno.y.paymentservice.aspect;


import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.invoke.MethodHandles;

@Aspect
@Component("webLoggingAspect")
public class LoggingAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // Pointcuts for HTTP methods
    @Pointcut("@annotation(org.springframework.web.bind.annotation.PostMapping)")
    public void allPostMappings() {}

    @Pointcut("@annotation(org.springframework.web.bind.annotation.PutMapping)")
    public void allPutMappings() {}

    @Pointcut("@annotation(org.springframework.web.bind.annotation.GetMapping)")
    public void allGetMappings() {}

    // Combined pointcut
    @Pointcut("allPostMappings() || allPutMappings() || allGetMappings()")
    public void restEndpoints() {}

    @Before("restEndpoints()")
    public void logBeforeRestCall(JoinPoint joinPoint) {
        HttpServletRequest httpServletRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        LOGGER.info("Request received | method={} | uri={} | handler={}",
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                joinPoint.getSignature().getName());
    }

    @AfterReturning(value = "restEndpoints()", returning = "responseObj")
    public void logAfterSuccessfulRestCall(JoinPoint joinPoint, Object responseObj) {
        HttpServletRequest httpServletRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        LOGGER.info("Request serviced successful | method={} | uri={}",
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI());
    }

    @AfterThrowing(value = "restEndpoints()", throwing = "exception")
    public void logAfterFailedRestResponse(JoinPoint joinPoint, Throwable exception) {
        HttpServletRequest httpServletRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        LOGGER.error("Request serviced with error | method={} | uri={} | error={} | message={}",
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                exception.getClass().getSimpleName(),
                exception.getMessage());
    }

    @Around("execution(* uno.y.paymentservice.service.*.*(..))")
    public Object logAround(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        LOGGER.debug("Entering method: {}", proceedingJoinPoint.getSignature());

        try {
            Object result = proceedingJoinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            LOGGER.debug("Exiting method: {} | executionTime={}ms",
                    proceedingJoinPoint.getSignature(), executionTime);
            return result;
        } catch (Throwable e) {
            long executionTime = System.currentTimeMillis() - startTime;
            LOGGER.error("Method failed: {} | executionTime={}ms",
                    proceedingJoinPoint.getSignature(), executionTime, e);
            throw e;
        }
    }
}
