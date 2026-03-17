package com.td.application.documents;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.documents.BusinessDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DocumentRepository extends IRepository<BusinessDocument> {

    Page<BusinessDocument> search(SearchDocumentsRequest request, Pageable pageable);

    Page<BusinessDocument> searchDeleted(SearchDocumentsRequest request, Pageable pageable);

    void hardDelete(BusinessDocument entity);
}
