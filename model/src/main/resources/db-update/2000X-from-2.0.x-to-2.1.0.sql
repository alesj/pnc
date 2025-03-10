--
-- JBoss, Home of Professional Open Source.
-- Copyright 2014-2020 Red Hat, Inc., and individual contributors
-- as indicated by the @author tags.
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- [NCL-6158]: Introduce new field BuildConfiguration.brewPullActive, which defaults to false, but existing records
-- should have it active
BEGIN transaction;
    ALTER TABLE buildconfiguration ADD COLUMN brewpullactive boolean;
    UPDATE buildconfiguration SET brewPullActive=true;
    ALTER TABLE buildconfiguration ALTER COLUMN brewpullactive SET NOT NULL;

    ALTER TABLE buildconfiguration_aud ADD COLUMN brewpullactive boolean;
    UPDATE buildconfiguration SET brewPullActive=true;
COMMIT;

BEGIN transaction;
    ALTER TABLE ProductMilestone ADD COLUMN distributedArtifactsImporter_id integer;

    ALTER TABLE ProductMilestone ADD CONSTRAINT fk_distributed_artifacts_importer_user
    FOREIGN KEY (distributedArtifactsImporter_id) REFERENCES usertable(id);
COMMIT;

-- [NCL-5738] - build record using GUID
BEGIN transaction;
    ALTER TABLE buildrecord ALTER COLUMN id SET DATA TYPE bigint;
    ALTER TABLE buildrecord ALTER COLUMN norebuildcause_id SET DATA TYPE bigint;
    ALTER TABLE artifact ALTER COLUMN buildrecord_id SET DATA TYPE bigint;
    ALTER TABLE build_record_artifact_dependencies_map ALTER COLUMN build_record_id SET DATA TYPE bigint;
    ALTER TABLE build_record_attributes ALTER COLUMN build_record_id SET DATA TYPE bigint;
    ALTER TABLE buildrecordpushresult ALTER COLUMN buildrecord_id SET DATA TYPE bigint;
    ALTER TABLE _archived_buildrecords ALTER COLUMN buildrecord_id SET DATA TYPE bigint;
COMMIT;

-- [NCL-6361] Add to the Artifacts model a new field to store the build category
BEGIN transaction;
    ALTER TABLE artifact ADD COLUMN buildCategory varchar(50);
    UPDATE artifact SET buildCategory='STANDARD';

    ALTER TABLE artifact_aud ADD COLUMN buildCategory varchar(50);
    UPDATE artifact_aud SET buildCategory='STANDARD';
COMMIT;

-- Add pattern index on identifier in the artifact table
BEGIN transaction;
    CREATE INDEX idx_artifact_identifier_patt ON artifact (identifier text_pattern_ops);
COMMIT;

