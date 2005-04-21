package org.apache.maven.tools.repoclean.translate;

import org.apache.maven.model.Build;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Developer;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Goal;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Notifier;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Reports;
import org.apache.maven.model.Repository;
import org.apache.maven.model.Resource;
import org.apache.maven.model.Scm;
import org.apache.maven.model.Site;
import org.apache.maven.tools.repoclean.report.ReportWriteException;
import org.apache.maven.tools.repoclean.report.Reporter;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * ==================================================================== Copyright 2001-2004 The
 * Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License. ====================================================================
 */

/**
 * @author jdcasey
 */
public class PomV3ToV4Translator
    extends AbstractLogEnabled
{

    public static final String ROLE = PomV3ToV4Translator.class.getName();
    
    private transient List discoveredPlugins = new ArrayList();

    public Model translate( org.apache.maven.model.v3_0_0.Model v3Model, Reporter reporter )
        throws ReportWriteException
    {
        try
        {
            String groupId = format( v3Model.getGroupId() );
            String artifactId = format( v3Model.getArtifactId() );

            String id = format( v3Model.getId() );
            if ( StringUtils.isNotEmpty( id ) )
            {
                if ( StringUtils.isEmpty( groupId ) )
                {
                    groupId = id;
                }

                if ( StringUtils.isEmpty( artifactId ) )
                {
                    artifactId = id;
                }
            }

            String version = format( v3Model.getCurrentVersion() );
            if ( version == null )
            {
                version = format( v3Model.getVersion() );
            }

            PomKey pomKey = new PomKey( groupId, artifactId, version );

            warnOfUnsupportedMainModelElements( v3Model, reporter );

            Model model = null;
            try
            {
                model = new Model();
                model.setArtifactId( artifactId );

                // moved this above the translation of the build, to allow
                // additional plugins to be defined in v3 poms via 
                // <dependency><type>plugin</type></dependency>
                model.setDependencies( translateDependencies( v3Model.getDependencies() ) );

                model.setBuild( translateBuild( v3Model.getBuild(), reporter ) );
                model.setCiManagement( translateCiManagementInfo( v3Model.getBuild() ) );
                model.setContributors( translateContributors( v3Model.getContributors() ) );

                model.setDescription( v3Model.getDescription() );
                model.setDevelopers( translateDevelopers( v3Model.getDevelopers() ) );

                model.setDistributionManagement( translateDistributionManagement( pomKey, v3Model ) );

                model.setGroupId( groupId );
                model.setInceptionYear( v3Model.getInceptionYear() );
                model.setIssueManagement( translateIssueManagement( v3Model ) );

                model.setLicenses( translateLicenses( v3Model.getLicenses() ) );
                model.setMailingLists( translateMailingLists( v3Model.getMailingLists() ) );
                model.setModelVersion( "4.0.0" );
                model.setName( v3Model.getName() );
                model.setOrganization( translateOrganization( v3Model.getOrganization(), reporter ) );
                model.setPackaging( "jar" );
                model.setReports( translateReports( v3Model.getReports(), reporter ) );
                model.setScm( translateScm( v3Model ) );
                model.setUrl( v3Model.getUrl() );

                model.setVersion( version );
            }
            catch ( PomTranslationException e )
            {
                reporter.error( "Invalid POM detected. Cannot translate.", e );
            }

            return model;
        }
        finally
        {
            this.discoveredPlugins.clear();
        }
    }

    private String format( String source )
    {
        return (source == null)?(null):(source.replace('+', '-'));
    }

    private CiManagement translateCiManagementInfo( org.apache.maven.model.v3_0_0.Build v3Build )
    {
        CiManagement ciMgmt = null;

        if ( v3Build != null )
        {
            String nagEmailAddress = v3Build.getNagEmailAddress();

            if ( StringUtils.isNotEmpty( nagEmailAddress ) )
            {
                Notifier notifier = new Notifier();

                notifier.setAddress( nagEmailAddress );
                notifier.setType( "mail" );

                ciMgmt = new CiManagement();
                ciMgmt.addNotifier( notifier );
            }
        }

        return ciMgmt;
    }

    private void warnOfUnsupportedMainModelElements( org.apache.maven.model.v3_0_0.Model v3Model, Reporter reporter )
        throws ReportWriteException
    {
        if ( StringUtils.isNotEmpty( v3Model.getExtend() ) )
        {
            reporter.warn( "Ignoring non-portable parent declaration: " + v3Model.getExtend() );
        }

        if ( StringUtils.isNotEmpty( v3Model.getGumpRepositoryId() ) )
        {
            reporter.warn( "Ignoring gump repository id: \'" + v3Model.getGumpRepositoryId()
                + "\'. This is not supported in v4 POMs." );
        }

        if ( notEmpty( v3Model.getVersions() ) )
        {
            reporter.warn( "Ignoring <versions/> section. This is not supported in v4 POMs." );
        }

        if ( notEmpty( v3Model.getBranches() ) )
        {
            reporter.warn( "Ignoring <branches/> section. This is not supported in v4 POMs." );
        }

        Properties v3ModelProperties = v3Model.getProperties();

        if ( v3ModelProperties != null && !v3ModelProperties.isEmpty() )
        {
            reporter.warn( "Ignoring <properties/> section. It is not supported in v4 POMs." );
        }

        if ( StringUtils.isNotEmpty( v3Model.getPackage() ) )
        {
            reporter.warn( "Ignoring <package/>. It is not supported in v4 POMs." );
        }

        if ( notEmpty( v3Model.getPackageGroups() ) )
        {
            reporter.warn( "Ignoring <packageGroups/> section. It is not supported in v4 POMs." );
        }

        if ( StringUtils.isNotEmpty( v3Model.getLogo() ) )
        {
            reporter.warn( "Ignoring <logo/> for project. It is not supported in v4 POMs." );
        }

        if ( StringUtils.isNotEmpty( v3Model.getShortDescription() ) )
        {
            reporter.warn( "Ignoring <shortDescription/>. It is not supported in v4 POMs." );
        }
    }

    private Scm translateScm( org.apache.maven.model.v3_0_0.Model v3Model )
    {
        Scm scm = null;

        org.apache.maven.model.v3_0_0.Repository repo = v3Model.getRepository();
        if ( repo != null )
        {
            scm = new Scm();
            scm.setConnection( repo.getConnection() );
            scm.setDeveloperConnection( repo.getDeveloperConnection() );
            scm.setUrl( repo.getUrl() );
        }

        return scm;
    }

    private Reports translateReports( List v3Reports, Reporter reporter )
        throws ReportWriteException
    {
        Reports reports = null;
        if ( v3Reports != null && !v3Reports.isEmpty() )
        {
            reports = new Reports();
            for ( Iterator it = v3Reports.iterator(); it.hasNext(); )
            {
                String reportName = (String) it.next();

                Pattern pluginNamePattern = Pattern.compile( "maven-(.+)-plugin" );
                Matcher matcher = pluginNamePattern.matcher( reportName );

                String reportPluginName = null;
                if ( !matcher.matches() )
                {
                    reporter.warn( "Non-standard report name: \'" + reportName
                        + "\'. Using entire name for plugin artifactId." );

                    reportPluginName = reportName;
                }
                else
                {
                    reportPluginName = matcher.group( 1 );
                }

                Plugin reportPlugin = new Plugin();

                reportPlugin.setGroupId( "maven" );

                reportPlugin.setArtifactId( reportPluginName );

                reportPlugin.setVersion( "1.0-SNAPSHOT" );

                StringBuffer info = new StringBuffer();

                info.append( "Using some contrived information for report: \'" ).append( reportName ).append( "\'.\n" ).append(
                                                                                                                                "\to groupId: \'maven\'\n" ).append(
                                                                                                                                                                     "\to artifactId: \'" ).append(
                                                                                                                                                                                                    reportPluginName ).append(
                                                                                                                                                                                                                               "\'\n" ).append(
                                                                                                                                                                                                                                                "\to version: \'1.0-SNAPSHOT\'\n" ).append(
                                                                                                                                                                                                                                                                                            "\to goal: \'report\'\n" ).append(
                                                                                                                                                                                                                                                                                                                               "\n" ).append(
                                                                                                                                                                                                                                                                                                                                              "These values were extracted using the v3 report naming convention, but may be wrong." );

                reporter.warn( info.toString() );

                Goal reportGoal = new Goal();

                reportGoal.setId( "report" );

                reportPlugin.addGoal( reportGoal );

                reports.addPlugin( reportPlugin );
            }
        }

        return reports;
    }

    private org.apache.maven.model.Organization translateOrganization(
                                                                      org.apache.maven.model.v3_0_0.Organization v3Organization,
                                                                      Reporter reporter )
        throws ReportWriteException
    {
        Organization organization = null;

        if ( v3Organization != null )
        {
            organization = new Organization();

            organization.setName( v3Organization.getName() );
            organization.setUrl( v3Organization.getUrl() );

            if ( StringUtils.isNotEmpty( v3Organization.getLogo() ) )
            {
                reporter.warn( "Ignoring <organization><logo/></organization>. It is not supported in v4 POMs." );
            }
        }

        return organization;
    }

    private List translateMailingLists( List v3MailingLists )
    {
        List mailingLists = new ArrayList();

        if ( notEmpty( v3MailingLists ) )
        {
            for ( Iterator it = v3MailingLists.iterator(); it.hasNext(); )
            {
                org.apache.maven.model.v3_0_0.MailingList v3List = (org.apache.maven.model.v3_0_0.MailingList) it.next();
                MailingList list = new MailingList();
                list.setArchive( v3List.getArchive() );
                list.setName( v3List.getName() );
                list.setSubscribe( v3List.getSubscribe() );
                list.setUnsubscribe( v3List.getUnsubscribe() );

                mailingLists.add( list );
            }
        }

        return mailingLists;
    }

    private List translateLicenses( List v3Licenses )
    {
        List licenses = new ArrayList();

        if ( notEmpty( v3Licenses ) )
        {
            for ( Iterator it = v3Licenses.iterator(); it.hasNext(); )
            {
                org.apache.maven.model.v3_0_0.License v3License = (org.apache.maven.model.v3_0_0.License) it.next();
                License license = new License();
                license.setComments( v3License.getComments() );
                license.setName( v3License.getName() );
                license.setUrl( v3License.getUrl() );

                licenses.add( license );
            }
        }

        return licenses;
    }

    private IssueManagement translateIssueManagement( org.apache.maven.model.v3_0_0.Model v3Model )
    {
        IssueManagement issueMgmt = null;

        String issueTrackingUrl = v3Model.getIssueTrackingUrl();
        if ( StringUtils.isNotEmpty( issueTrackingUrl ) )
        {
            issueMgmt = new IssueManagement();
            issueMgmt.setUrl( issueTrackingUrl );
        }

        return issueMgmt;
    }

    private DistributionManagement translateDistributionManagement( PomKey pomKey,
                                                                   org.apache.maven.model.v3_0_0.Model v3Model )
        throws PomTranslationException
    {
        DistributionManagement distributionManagement = new DistributionManagement();

        Site site = null;

        String siteAddress = v3Model.getSiteAddress();

        String siteDirectory = v3Model.getSiteDirectory();

        if ( StringUtils.isEmpty( siteAddress ) )
        {
            if ( !StringUtils.isEmpty( siteDirectory ) )
            {
                site = new Site();

                site.setId( "default" );

                site.setName( "Default Site" );

                site.setUrl( "file://" + siteDirectory );
            }
        }
        else
        {
            if ( StringUtils.isEmpty( siteDirectory ) )
            {
                throw new PomTranslationException( pomKey.groupId(), pomKey.artifactId(), pomKey.version(),
                                                   "Missing 'siteDirectory': Both siteAddress and siteDirectory must be set at the same time." );
            }

            site = new Site();

            site.setId( "default" );

            site.setName( "Default Site" );

            site.setUrl( "scp://" + siteAddress + "/" + siteDirectory );
        }

        distributionManagement.setSite( site );

        String distributionSite = v3Model.getDistributionSite();

        String distributionDirectory = v3Model.getDistributionDirectory();

        Repository repository = null;

        if ( StringUtils.isEmpty( distributionSite ) )
        {
            if ( !StringUtils.isEmpty( distributionDirectory ) )
            {
                repository = new Repository();

                repository.setId( "default" );

                repository.setName( "Default Repository" );

                repository.setUrl( "file://" + distributionDirectory );
                //                throw new Exception( "Missing 'distributionSite': Both distributionSite and
                // distributionDirectory must be set." );
            }
        }
        else
        {
            if ( StringUtils.isEmpty( distributionDirectory ) )
            {
                throw new PomTranslationException( pomKey.groupId(), pomKey.artifactId(), pomKey.version(),
                                                   "Missing 'distributionDirectory': must be set is 'distributionSite' is set." );
            }

            repository = new Repository();

            repository.setId( "default" );

            repository.setName( "Default Repository" );

            repository.setUrl( distributionSite + "/" + distributionDirectory );
        }

        distributionManagement.setRepository( repository );

        if ( site == null && repository == null )
        {
            return null;
        }

        return distributionManagement;
    }

    private List translateDevelopers( List v3Developers )
    {
        List developers = new ArrayList();

        if ( notEmpty( v3Developers ) )
        {
            for ( Iterator it = v3Developers.iterator(); it.hasNext(); )
            {
                org.apache.maven.model.v3_0_0.Developer v3Developer = (org.apache.maven.model.v3_0_0.Developer) it.next();

                Developer developer = new Developer();

                developer.setEmail( v3Developer.getEmail() );
                developer.setId( v3Developer.getId() );
                developer.setName( v3Developer.getName() );
                developer.setOrganization( v3Developer.getOrganization() );
                developer.setRoles( v3Developer.getRoles() );
                developer.setTimezone( v3Developer.getTimezone() );
                developer.setUrl( v3Developer.getUrl() );

                developers.add( developer );
            }
        }

        return developers;
    }

    private List translateDependencies( List v3Deps )
    {
        List deps = new ArrayList();

        if ( notEmpty( v3Deps ) )
        {
            for ( Iterator it = v3Deps.iterator(); it.hasNext(); )
            {
                org.apache.maven.model.v3_0_0.Dependency v3Dep = (org.apache.maven.model.v3_0_0.Dependency) it.next();

                String groupId = format( v3Dep.getGroupId() );
                String artifactId = format( v3Dep.getArtifactId() );
                
                String id = format( v3Dep.getId() );
                
                if( StringUtils.isNotEmpty( id ) )
                {
                    if( StringUtils.isEmpty( groupId ) )
                    {
                        groupId = id;
                    }
                    
                    if( StringUtils.isEmpty( artifactId ) )
                    {
                        artifactId = id;
                    }
                }

                String type = v3Dep.getType();
                if( "plugin".equals( type ) )
                {
                    if( "maven".equals( groupId ) )
                    {
                        groupId = "org.apache.maven.plugins";
                    }
                    
                    Plugin plugin = new Plugin();
                    plugin.setGroupId( groupId );
                    plugin.setArtifactId( artifactId );
                    plugin.setVersion( format( v3Dep.getVersion() ) );

                    Xpp3Dom config = new Xpp3Dom( "configuration" );

                    Properties props = v3Dep.getProperties();
                    
                    if ( !props.isEmpty() )
                    {
                        for ( Iterator propertyIterator = props.keySet().iterator(); it.hasNext(); )
                        {
                            String key = (String) propertyIterator.next();
                            String value = props.getProperty( key );
                            
                            Xpp3Dom child = new Xpp3Dom( key );
                            child.setValue( value );
                            
                            config.addChild( child );
                        }
                    }
                    
                    plugin.setConfiguration( config );
                    
                    this.discoveredPlugins.add( plugin );
                }
                else
                {
                    Dependency dep = new Dependency();

                    dep.setGroupId(groupId);
                    dep.setArtifactId(artifactId);
                    dep.setVersion( v3Dep.getVersion() );
                    dep.setType( v3Dep.getType() );
                    
                    String scope = v3Dep.getProperty( "scope" );
                    if( StringUtils.isNotEmpty( scope ) )
                    {
                        dep.setScope( scope );
                    }

                    deps.add( dep );
                }
            }
        }

        return deps;
    }

    private List translateContributors( List v3Contributors )
    {
        List contributors = new ArrayList();

        if ( notEmpty( v3Contributors ) )
        {
            for ( Iterator it = v3Contributors.iterator(); it.hasNext(); )
            {
                org.apache.maven.model.v3_0_0.Contributor v3Contributor = (org.apache.maven.model.v3_0_0.Contributor) it.next();

                Contributor contributor = new Contributor();

                contributor.setEmail( v3Contributor.getEmail() );
                contributor.setName( v3Contributor.getName() );
                contributor.setOrganization( v3Contributor.getOrganization() );
                contributor.setRoles( v3Contributor.getRoles() );
                contributor.setTimezone( v3Contributor.getTimezone() );
                contributor.setUrl( v3Contributor.getUrl() );

                contributors.add( contributor );
            }
        }

        return contributors;
    }

    private Build translateBuild( org.apache.maven.model.v3_0_0.Build v3Build, Reporter reporter )
        throws ReportWriteException
    {
        Build build = null;
        if ( v3Build != null )
        {
            build = new Build();

            warnOfUnsupportedBuildElements( v3Build, reporter );

            build.setSourceDirectory( v3Build.getSourceDirectory() );
            build.setTestSourceDirectory( v3Build.getUnitTestSourceDirectory() );

            build.setResources( translateResources( v3Build.getResources() ) );

            org.apache.maven.model.v3_0_0.UnitTest unitTest = v3Build.getUnitTest();
            if ( unitTest != null )
            {
                build.setTestResources( translateResources( unitTest.getResources() ) );

                List testIncludes = unitTest.getIncludes();

                List testExcludes = new ArrayList( unitTest.getExcludes() );
                testExcludes.addAll( unitTest.getDefaultExcludes() );

                if ( notEmpty( testIncludes ) || notEmpty( testExcludes ) )
                {
                    Plugin plugin = new Plugin();
                    plugin.setGroupId( "org.apache.maven.plugins" );
                    plugin.setArtifactId( "surefire" );
                    plugin.setVersion( "1.0-SNAPSHOT" );

                    Xpp3Dom config = new Xpp3Dom( "configuration" );

                    if ( notEmpty( testIncludes ) )
                    {
                        Xpp3Dom includes = new Xpp3Dom( "includes" );
                        for ( Iterator it = testIncludes.iterator(); it.hasNext(); )
                        {
                            String includePattern = (String) it.next();
                            Xpp3Dom include = new Xpp3Dom( "include" );
                            include.setValue( includePattern );

                            includes.addChild( include );
                        }

                        config.addChild( includes );
                    }

                    if ( notEmpty( testExcludes ) )
                    {
                        Xpp3Dom excludes = new Xpp3Dom( "excludes" );
                        for ( Iterator it = testExcludes.iterator(); it.hasNext(); )
                        {
                            String excludePattern = (String) it.next();
                            Xpp3Dom exclude = new Xpp3Dom( "exclude" );
                            exclude.setValue( excludePattern );

                            excludes.addChild( exclude );
                        }

                        config.addChild( excludes );
                    }

                    if ( config.getChildCount() > 0 )
                    {
                        plugin.setConfiguration( config );
                    }

                    build.addPlugin( plugin );
                }
            }
        }
        
        if(!this.discoveredPlugins.isEmpty())
        {
            if(build == null)
            {
                build = new Build();
            }
            
            for ( Iterator it = this.discoveredPlugins.iterator(); it.hasNext(); )
            {
                Plugin plugin = (Plugin) it.next();
                
                build.addPlugin( plugin );
            }
        }

        return build;
    }

    private void warnOfUnsupportedBuildElements( org.apache.maven.model.v3_0_0.Build v3Build, Reporter reporter )
        throws ReportWriteException
    {
        if ( notEmpty( v3Build.getSourceModifications() ) )
        {
            reporter.warn( "Ignoring <sourceModifications/> section. It is not supported in v4 POMs." );
        }

        if ( StringUtils.isNotEmpty( v3Build.getAspectSourceDirectory() ) )
        {
            reporter.warn( "Ignoring <aspectSourceDirectory/>. It is not supported in v4 POMs." );
        }

        if ( StringUtils.isNotEmpty( v3Build.getIntegrationUnitTestSourceDirectory() ) )
        {
            reporter.warn( "Ignoring <integrationUnitTestSourceDirectory/>. It is not supported in v4 POMs." );
        }
    }

    private List translateResources( List v3Resources )
    {
        List resources = new ArrayList();

        if ( notEmpty( v3Resources ) )
        {
            for ( Iterator it = v3Resources.iterator(); it.hasNext(); )
            {
                org.apache.maven.model.v3_0_0.Resource v3Resource = (org.apache.maven.model.v3_0_0.Resource) it.next();
                Resource resource = new Resource();

                resource.setDirectory( v3Resource.getDirectory() );
                
                List excludes = new ArrayList(v3Resource.getExcludes());
                excludes.removeAll(v3Resource.getDefaultExcludes());
                
                resource.setExcludes( excludes );
                
                resource.setIncludes( v3Resource.getIncludes() );
                resource.setTargetPath( v3Resource.getTargetPath() );

                resources.add( resource );
            }
        }

        return resources;
    }

    //    private String pathPatternsToString( List patterns )
    //    {
    //        StringBuffer result = new StringBuffer();
    //
    //        if ( notEmpty( patterns ) )
    //        {
    //            for ( Iterator it = patterns.iterator(); it.hasNext(); )
    //            {
    //                String pattern = (String) it.next();
    //
    //                result.append( "," ).append( pattern );
    //            }
    //
    //            result.setLength( result.length() - 1 );
    //        }
    //
    //        return result.toString();
    //    }

    private boolean notEmpty( List test )
    {
        return test != null && !test.isEmpty();
    }

    private static class PomKey
    {
        private final String groupId;

        private final String artifactId;

        private final String version;

        PomKey( String groupId, String artifactId, String version )
        {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        public String groupId()
        {
            return groupId;
        }

        public String artifactId()
        {
            return artifactId;
        }

        public String version()
        {
            return version;
        }
    }

}