-- Which layout a resume renders and exports with.
ALTER TABLE generated_documents ADD COLUMN template VARCHAR(20) NOT NULL DEFAULT 'CLASSIC';
