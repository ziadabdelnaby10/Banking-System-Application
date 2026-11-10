package org.bank.customer.service;

import org.bank.common.dto.response.BranchResponse;
import org.bank.common.dto.request.CreateBranchRequest;
import org.bank.common.dto.request.UpdateBranchRequest;

import java.util.List;

public interface BranchService {

    BranchResponse create(CreateBranchRequest request);

    BranchResponse get(String code);

    /** Branches the caller may see: all for unrestricted callers, only their own otherwise. */
    List<BranchResponse> list();

    BranchResponse update(String code, UpdateBranchRequest request);
}
