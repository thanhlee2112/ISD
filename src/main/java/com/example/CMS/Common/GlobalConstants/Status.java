package com.example.CMS.Common.GlobalConstants;

/** Stored as ordinals and sent to the frontend as numbers: do not reorder. */
public enum Status {
    DRAFT,         // 0 - being written by the author
    PENDING,       // 1 - sent to the editors, waiting for review
    UNDER_REVIEW,  // 2 - an editor is working on it
    PUBLISHED,     // 3
    RETURNED       // 4 - sent back to the author
}
