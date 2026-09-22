package com.shreeya.vehicletitle.application;

import com.shreeya.vehicletitle.domain.TitleTransaction;

public record SubmissionResult(TitleTransaction transaction, boolean idempotentReplay) {
}
