-- Historical representations keep NULL: their coverage was not measured by the v2 pipeline.
ALTER TABLE response_embedding ADD COLUMN analysis_details jsonb;
ALTER TABLE response_embedding ADD CONSTRAINT ck_response_analysis_details_object
    CHECK (analysis_details IS NULL OR jsonb_typeof(analysis_details) = 'object');
