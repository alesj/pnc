/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.bpm.causeway;

import org.jboss.pnc.bpm.BpmEventType;
import org.jboss.pnc.bpm.BpmManager;
import org.jboss.pnc.bpm.BpmTask;
import org.jboss.pnc.bpm.NoEntityException;
import org.jboss.pnc.bpm.RestConnector;
import org.jboss.pnc.bpm.model.causeway.BuildImportResultRest;
import org.jboss.pnc.bpm.model.causeway.BuildImportStatus;
import org.jboss.pnc.bpm.model.causeway.MilestoneReleaseResultRest;
import org.jboss.pnc.bpm.task.MilestoneReleaseTask;
import org.jboss.pnc.common.Numbers;
import org.jboss.pnc.common.concurrent.Sequence;
import org.jboss.pnc.common.json.GlobalModuleGroup;
import org.jboss.pnc.common.json.moduleconfig.BpmModuleConfig;
import org.jboss.pnc.dto.ProductMilestoneCloseResult;
import org.jboss.pnc.enums.BuildPushStatus;
import org.jboss.pnc.enums.MilestoneCloseStatus;
import org.jboss.pnc.mapper.api.BuildMapper;
import org.jboss.pnc.mapper.api.ProductMilestoneCloseResultMapper;
import org.jboss.pnc.model.Base32LongID;
import org.jboss.pnc.model.BuildRecord;
import org.jboss.pnc.model.BuildRecordPushResult;
import org.jboss.pnc.model.ProductMilestone;
import org.jboss.pnc.model.ProductMilestoneRelease;
import org.jboss.pnc.model.ProductVersion;
import org.jboss.pnc.spi.datastore.repositories.BuildRecordPushResultRepository;
import org.jboss.pnc.spi.datastore.repositories.BuildRecordRepository;
import org.jboss.pnc.spi.datastore.repositories.ProductMilestoneReleaseRepository;
import org.jboss.pnc.spi.datastore.repositories.ProductMilestoneRepository;
import org.jboss.pnc.spi.datastore.repositories.ProductVersionRepository;
import org.jboss.pnc.spi.exception.CoreException;
import org.jboss.pnc.spi.exception.ProcessManagerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.function.Function;

import static org.jboss.pnc.common.util.CollectionUtils.ofNullableCollection;

/**
 * Author: Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com Date: 8/31/16 Time: 8:42 AM
 */
@Stateless
public class ProductMilestoneReleaseManager {

    private static final Logger log = LoggerFactory.getLogger(ProductMilestoneReleaseManager.class);
    private static final Logger userLog = LoggerFactory.getLogger("org.jboss.pnc._userlog_.milestone");

    private BpmManager bpmManager;

    private ProductVersionRepository productVersionRepository;
    private BuildRecordRepository buildRecordRepository;
    private ProductMilestoneReleaseRepository productMilestoneReleaseRepository;
    private ProductMilestoneRepository milestoneRepository;
    private BuildRecordPushResultRepository buildRecordPushResultRepository;

    private Event<ProductMilestoneCloseResult> productMilestoneCloseResultEvent;
    private ProductMilestoneCloseResultMapper mapper;
    private GlobalModuleGroup globalConfig;
    private BpmModuleConfig bpmConfig;

    @Deprecated // for ejb
    public ProductMilestoneReleaseManager() {
    }

    @Inject
    public ProductMilestoneReleaseManager(
            ProductMilestoneReleaseRepository productMilestoneReleaseRepository,
            BpmManager bpmManager,
            ProductVersionRepository productVersionRepository,
            BuildRecordRepository buildRecordRepository,
            ProductMilestoneRepository milestoneRepository,
            BuildRecordPushResultRepository buildRecordPushResultRepository,
            ProductMilestoneCloseResultMapper mapper,
            Event<ProductMilestoneCloseResult> productMilestoneCloseResultEvent,
            GlobalModuleGroup globalConfig,
            BpmModuleConfig bpmConfig) {
        this.productMilestoneReleaseRepository = productMilestoneReleaseRepository;
        this.bpmManager = bpmManager;
        this.productVersionRepository = productVersionRepository;
        this.buildRecordRepository = buildRecordRepository;
        this.milestoneRepository = milestoneRepository;
        this.buildRecordPushResultRepository = buildRecordPushResultRepository;
        this.mapper = mapper;
        this.productMilestoneCloseResultEvent = productMilestoneCloseResultEvent;
        this.globalConfig = globalConfig;
        this.bpmConfig = bpmConfig;
    }

    /**
     * Starts milestone release process
     *
     * @param milestone product milestone to start the release for
     * @param accessToken
     * @param milestoneReleaseId
     * @return
     */
    public ProductMilestoneRelease startRelease(
            ProductMilestone milestone,
            String accessToken,
            boolean useRhpam,
            Long milestoneReleaseId) {

        ProductMilestoneRelease release;
        if (useRhpam) {
            release = triggerRHPAMRelease(milestone, accessToken, milestoneReleaseId);
        } else {
            release = triggerRelease(milestone, accessToken, milestoneReleaseId);
        }
        return productMilestoneReleaseRepository.save(release);
    }

    public void cancel(ProductMilestone milestoneInDb, String accessToken, boolean useRHPAM) {
        if (!useRHPAM) {
            Collection<BpmTask> activeTasks = bpmManager.getActiveTasks();
            Optional<MilestoneReleaseTask> milestoneReleaseTask = activeTasks.stream()
                    .map(task -> (MilestoneReleaseTask) task)
                    .filter(task -> task.getMilestone().getId().equals(milestoneInDb.getId()))
                    .findAny();
            if (milestoneReleaseTask.isPresent()) {
                bpmManager.cancelTask(milestoneReleaseTask.get());
            }
        } else {
            RestConnector restConnector = new RestConnector(bpmConfig);
            restConnector.cancelByCorrelation(Numbers.decimalToBase32(milestoneInDb.getId()), accessToken);
        }

        ProductMilestoneRelease milestoneRelease = productMilestoneReleaseRepository
                .findLatestByMilestone(milestoneInDb);
        milestoneRelease.setStatus(MilestoneCloseStatus.CANCELED);
        productMilestoneReleaseRepository.save(milestoneRelease);
    }

    public boolean noReleaseInProgress(ProductMilestone milestone) {
        return !getInProgress(milestone).isPresent();
    }

    public Optional<ProductMilestoneRelease> getInProgress(ProductMilestone milestone) {
        ProductMilestoneRelease latestRelease = productMilestoneReleaseRepository.findLatestByMilestone(milestone);
        if (latestRelease != null && latestRelease.getStatus() == MilestoneCloseStatus.IN_PROGRESS) {
            return Optional.of(latestRelease);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Trigger the release using the new RHPAM server
     * 
     * @param milestone milestone to be released
     * @param accessToken access token to use to submit request
     * @param milestoneReleaseId the milestone id
     */
    private ProductMilestoneRelease triggerRHPAMRelease(
            ProductMilestone milestone,
            String accessToken,
            Long milestoneReleaseId) {

        ProductMilestoneRelease release = new ProductMilestoneRelease();
        release.setId(milestoneReleaseId);
        release.setStartingDate(new Date());
        release.setMilestone(milestone);

        try (RestConnector restConnector = new RestConnector(bpmConfig)) {
            MilestoneReleaseTask releaseTask = new MilestoneReleaseTask(milestone, accessToken);
            releaseTask.setTaskId(bpmManager.getNextTaskId());
            releaseTask.setGlobalConfig(globalConfig);
            releaseTask.setJsonEncodedProcessParameters(false);
            restConnector.startProcess(
                    bpmConfig.getBpmNewReleaseProcessId(),
                    releaseTask.getExtendedProcessParameters(),
                    Numbers.decimalToBase32(milestoneReleaseId),
                    accessToken);
            return release;
        } catch (CoreException | ProcessManagerException e) {
            log.error("Error trying to start brew push task for milestone: {}", milestone.getId(), e);
            userLog.error("Release process creation failed.", e);
            release.setStatus(MilestoneCloseStatus.SYSTEM_ERROR);
            release.setEndDate(new Date());
            return release;
        }
    }

    /**
     * Accept result of product milestone close completed from RHPAM server
     *
     * @param milestoneReleaseResult result
     */
    public void productMilestoneCloseCompleted(MilestoneReleaseResultRest milestoneReleaseResult) {
        onPushResult(milestoneReleaseResult.getMilestoneId(), milestoneReleaseResult);
    }

    @Deprecated
    private ProductMilestoneRelease triggerRelease(
            ProductMilestone milestone,
            String accessToken,
            Long milestoneReleaseId) {
        ProductMilestoneRelease release = new ProductMilestoneRelease();
        release.setId(milestoneReleaseId);
        release.setStartingDate(new Date());
        release.setMilestone(milestone);
        try {
            MilestoneReleaseTask releaseTask = new MilestoneReleaseTask(milestone, accessToken);
            Integer id = milestone.getId();
            releaseTask.<MilestoneReleaseResultRest> addListener(BpmEventType.BREW_IMPORT, r -> onPushResult(id, r));
            release.setStatus(MilestoneCloseStatus.IN_PROGRESS);
            bpmManager.startTask(releaseTask);
            userLog.info("Release process started.");
            productMilestoneCloseResultEvent.fire(mapper.toDTO(release));
            return release;
        } catch (CoreException e) {
            log.error("Error trying to start brew push task for milestone: {}", milestone.getId(), e);
            userLog.error("Release process creation failed.", e);
            release.setStatus(MilestoneCloseStatus.SYSTEM_ERROR);
            release.setEndDate(new Date());
            productMilestoneCloseResultEvent.fire(mapper.toDTO(release));
            return release;
        }
    }

    private void onPushResult(Integer milestoneId, MilestoneReleaseResultRest result) {
        log.debug("Storing milestone release result: {}", result);
        ProductMilestone milestone = milestoneRepository.queryById(milestoneId);
        if (milestone == null) {
            log.error("No milestone found for milestone id {}", milestoneId);
            return;
        }
        storeResult(milestone, result);
        userLog.info("Milestone release result stored.");
    }

    private void storeResult(ProductMilestone milestone, MilestoneReleaseResultRest result) {
        ProductMilestoneRelease productMilestoneRelease = updateRelease(
                milestone,
                result.getReleaseStatus().getMilestoneReleaseStatus())
                        .orElseThrow(() -> new NoEntityException("ProductMilestoneRelease not found."));

        for (BuildImportResultRest buildRest : ofNullableCollection(result.getBuilds())) {
            storeBuildRecordPush(buildRest, productMilestoneRelease);
        }

        if (result.getReleaseStatus().getMilestoneReleaseStatus() == MilestoneCloseStatus.SUCCEEDED) {
            // set milestone end date to now when the release process is successful
            milestone.setEndDate(new Date());
            milestoneRepository.save(milestone);

            removeCurrentFlagFromMilestone(milestone);
        }
        productMilestoneCloseResultEvent.fire(mapper.toDTO(productMilestoneRelease));
    }

    private void storeBuildRecordPush(
            BuildImportResultRest buildRest,
            ProductMilestoneRelease productMilestoneRelease) {
        Base32LongID recordId = BuildMapper.idMapper.toEntity(buildRest.getBuildRecordId());
        BuildRecord record = buildRecordRepository.queryById(recordId);
        if (record == null) {
            log.error("No record found for record id: {}, skipped saving info: {}", recordId, buildRest);
            return;
        }

        BuildPushStatus status;
        try {
            status = convertStatus(buildRest.getStatus());
        } catch (ProcessManagerException e) {
            log.error("Cannot convert status.", e);
            throw new RuntimeException("Cannot convert status.", e);
        }

        BuildRecordPushResult buildRecordPush = BuildRecordPushResult.newBuilder()
                .id(Sequence.nextId())
                .buildRecord(record)
                .status(status)
                .brewBuildId(buildRest.getBrewBuildId())
                .brewBuildUrl(buildRest.getBrewBuildUrl())
                .tagPrefix("") // TODO tag!
                .productMilestoneRelease(productMilestoneRelease)
                .build();
        buildRecordPushResultRepository.save(buildRecordPush);
    }

    private BuildPushStatus convertStatus(BuildImportStatus status) throws ProcessManagerException {
        switch (status) {
            case SUCCESSFUL:
                return BuildPushStatus.SUCCESS;
            case FAILED:
                return BuildPushStatus.FAILED;
            case ERROR:
                return BuildPushStatus.SYSTEM_ERROR;
        }
        throw new ProcessManagerException("Invalid BuildImportStatus: " + status.toString());
    }

    /**
     *
     * @param milestone
     * @param status
     * @return null if ProductMilestoneRelease is not found
     */
    private Optional<ProductMilestoneRelease> updateRelease(ProductMilestone milestone, MilestoneCloseStatus status) {
        ProductMilestoneRelease release = productMilestoneReleaseRepository.findLatestByMilestone(milestone);
        if (release == null) {
            log.error("No milestone release found for milestone {}", milestone.getId());
            return Optional.empty();
        }
        if (status != MilestoneCloseStatus.IN_PROGRESS) {
            release.setEndDate(new Date());
        }
        release.setStatus(status);
        return Optional.of(productMilestoneReleaseRepository.save(release));
    }

    /**
     * [NCL-3112] Mark the milestone provided as not current
     *
     * @param milestone ProductMilestone to not be current anymore
     */
    private void removeCurrentFlagFromMilestone(ProductMilestone milestone) {
        ProductVersion productVersion = milestone.getProductVersion();

        if (productVersion.getCurrentProductMilestone() != null
                && productVersion.getCurrentProductMilestone().getId().equals(milestone.getId())) {

            productVersion.setCurrentProductMilestone(null);
            productVersionRepository.save(productVersion);
        }
    }

    private static <T, R> R orNull(T value, Function<T, R> f) {
        return value == null ? null : f.apply(value);
    }
}
