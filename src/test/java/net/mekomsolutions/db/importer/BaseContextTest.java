package net.mekomsolutions.db.importer;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

/**
 * Base class for all tests that require a spring application context
 */
@ExtendWith(SpringExtension.class)
@TestExecutionListeners({ DirtiesContextBeforeModesTestExecutionListener.class,
        DependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class })
@TestPropertySource(properties = "logging.level.net.mekomsolutions.db.importer=DEBUG")
public abstract class BaseContextTest {
	
	@Autowired
	protected ApplicationContext appContext;
	
}
