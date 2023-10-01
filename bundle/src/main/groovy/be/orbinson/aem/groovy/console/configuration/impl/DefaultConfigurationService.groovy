package be.orbinson.aem.groovy.console.configuration.impl

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import groovy.transform.Synchronized
import groovy.util.logging.Slf4j
import org.apache.jackrabbit.api.security.user.User
import org.apache.jackrabbit.api.security.user.UserManager
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.resource.ResourceResolverFactory
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Modified
import org.osgi.service.component.annotations.Reference
import org.osgi.service.metatype.annotations.Designate

@Component(service = ConfigurationService, immediate = true)
@Designate(ocd = ConfigurationServiceProperties)
@Slf4j("LOG")
class DefaultConfigurationService implements ConfigurationService {

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    private boolean emailEnabled

    private Set<String> emailRecipients

    private Set<String> allowedGroups

    private Set<String> allowedScheduledJobsGroups

    private boolean auditDisabled

    private boolean displayAllAuditRecords

    private long threadTimeout

    private boolean distributedExecutionEnabled

    private boolean author

    @Override
    boolean hasPermission(SlingHttpServletRequest request) {
        isAdminOrAllowedGroupMember(request, allowedGroups)
    }

    @Override
    boolean hasScheduledJobPermission(SlingHttpServletRequest request) {
        isAdminOrAllowedGroupMember(request, allowedScheduledJobsGroups)
    }

    @Override
    boolean isEmailEnabled() {
        emailEnabled
    }

    @Override
    Set<String> getEmailRecipients() {
        emailRecipients
    }

    @Override
    boolean isAuditDisabled() {
        auditDisabled
    }

    @Override
    boolean isDisplayAllAuditRecords() {
        displayAllAuditRecords
    }

    @Override
    long getThreadTimeout() {
        threadTimeout
    }

    @Override
    boolean isDistributedExecutionEnabled() {
        distributedExecutionEnabled
    }

    @Override
    boolean isAuthor() {
        return author
    }

    @Activate
    @Modified
    @Synchronized
    void activate(ConfigurationServiceProperties properties, BundleContext bundleContext) {
        emailEnabled = properties.emailEnabled()
        emailRecipients = (properties.emailRecipients() ?: []).findAll() as Set
        allowedGroups = (properties.allowedGroups() ?: []).findAll() as Set
        allowedScheduledJobsGroups = (properties.allowedScheduledJobsGroups() ?: []).findAll() as Set
        auditDisabled = properties.auditDisabled()
        displayAllAuditRecords = properties.auditDisplayAll()
        threadTimeout = properties.threadTimeout()
        distributedExecutionEnabled = properties.distributedExecutionEnabled()
        if (bundleContext.getProperty("sling.run.modes") != null) {
            author = bundleContext.getProperty("sling.run.modes").contains("author")
        }
    }

    private boolean isAdminOrAllowedGroupMember(SlingHttpServletRequest request, Set<String> groupIds) {
        resourceResolverFactory.getServiceResourceResolver(null).withCloseable { resourceResolver ->
            def userManager = resourceResolver.adaptTo(UserManager);
            if (userManager != null) {
                def user = userManager.getAuthorizable(request.userPrincipal) as User
                def memberOfGroupIds = user.memberOf()*.ID

                LOG.debug("member of group IDs : {}, allowed group IDs : {}", memberOfGroupIds, groupIds)

                user.admin || (groupIds ? memberOfGroupIds.intersect(groupIds as Iterable) : false)
            } else {
                LOG.debug("UserManager not available, probably in a Sling Based application, falling back to is admin check")
                return request.getResourceResolver().getUserID() == "admin"
            }
        }
    }
}
