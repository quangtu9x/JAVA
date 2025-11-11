# Elasticsearch Integration Guide

*TD WebAPI - Elasticsearch Search & Analytics*

## 📋 Overview

Dự án TD WebAPI đã được tích hợp hoàn chỉnh với **Elasticsearch 8.11.3** để cung cấp các tính năng tìm kiếm nâng cao, autocomplete, và analytics. Elasticsearch được sử dụng song song với PostgreSQL để tối ưu hóa performance cho các operations tìm kiếm phức tạp.

## 🏗️ Architecture

### Search Architecture
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   PostgreSQL    │    │  Elasticsearch   │    │     Kibana      │
│  (Source Data)  │◄──►│ (Search Index)   │◄──►│  (Analytics)    │
│                 │    │                  │    │   (Optional)    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         ▲                        ▲                       ▲
         │                        │                       │
         ▼                        ▼                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                    TD WebAPI Application                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │   Domain    │  │ Application │  │     Infrastructure      │ │
│  │   Entities  │  │ Use Cases   │  │    Search Services      │ │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Key Components

1. **Document Entities** (`td-domain`)
   - `ProductDocument`: Elasticsearch representation của Product
   - `BrandDocument`: Elasticsearch representation của Brand

2. **Search Repositories** (`td-infrastructure`)
   - `ProductElasticsearchRepository`: Advanced product search operations
   - `BrandElasticsearchRepository`: Brand search và autocomplete

3. **Search Services** (`td-infrastructure`)
   - `SearchService`: Core search functionality với aggregations
   - `DataSynchronizationService`: Sync data từ PostgreSQL

4. **Use Cases** (`td-application`)
   - `AdvancedSearchProductsUseCase`: Complex product filtering
   - `SearchSuggestionsUseCase`: Autocomplete suggestions

5. **REST Controllers** (`td-web`)
   - `SearchController`: Public search endpoints
   - `SearchAdminController`: Admin data sync endpoints

## 🔧 Configuration

### Docker Compose
```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.11.3
  container_name: td-elasticsearch
  ports:
    - "9200:9200"
    - "9300:9300"
  environment:
    - discovery.type=single-node
    - cluster.name=td-cluster
    - xpack.security.enabled=false
    - "ES_JAVA_OPTS=-Xms1g -Xmx1g"
```

### Application Configuration
```yaml
elasticsearch:
  hosts:
    - localhost:9200
  cluster-name: td-cluster
  index:
    number-of-shards: 1
    number-of-replicas: 0
    analysis:
      default-analyzer: standard
      enable-vietnamese-analysis: true
      autocomplete:
        enabled: true
        min-gram: 2
        max-gram: 20
```

## 🚀 Getting Started

### 1. Start Elasticsearch
```powershell
# Using setup script (recommended)
.\setup-dev.ps1

# Or manually
docker-compose up -d elasticsearch
```

### 2. Verify Elasticsearch
```powershell
# Check cluster health
Invoke-RestMethod -Uri "http://localhost:9200/_cluster/health"

# Check indices
Invoke-RestMethod -Uri "http://localhost:9200/_cat/indices?v"
```

### 3. Index Initial Data
```powershell
# After starting the application
curl -X POST http://localhost:8080/api/v1/admin/search/sync/all \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

## 📊 Search Features

### Advanced Product Search
```bash
POST /api/v1/search/products/advanced
```
**Features:**
- Multi-field search (name, description, brand)
- Fuzzy matching với configurable fuzziness
- Price range filtering
- Brand và category filtering
- Rating-based filtering
- Custom sorting options
- Pagination support

**Example Request:**
```json
{
  "query": "laptop gaming",
  "brandIds": ["1", "2"],
  "categories": ["electronics", "gaming"],
  "minPrice": 500.00,
  "maxPrice": 2000.00,
  "minRating": 4.0,
  "page": 0,
  "size": 20,
  "sortBy": "price",
  "sortDirection": "asc"
}
```

### Autocomplete & Suggestions
```bash
GET /api/v1/search/suggestions?q=lap&limit=10
```
**Features:**
- Real-time autocomplete suggestions
- Combined product và brand suggestions
- Configurable suggestion types
- Minimum character requirements
- Performance-optimized với edge n-grams

### Global Search
```bash
GET /api/v1/search/global?q=samsung&page=0&size=20
```
**Features:**
- Simultaneous search across products và brands
- Unified result scoring
- Cross-entity relevance ranking

### Analytics & Aggregations
```bash
GET /api/v1/search/analytics?q=laptop
```
**Features:**
- Brand distribution trong search results
- Category breakdowns
- Price range analysis
- Average rating calculations
- Search result statistics

## 📱 API Examples

### PowerShell Examples

#### Get Access Token
```powershell
$tokenResponse = Invoke-RestMethod -Uri "http://localhost:8180/realms/td-webapi-realm/protocol/openid-connect/token" `
  -Method POST -ContentType "application/x-www-form-urlencoded" `
  -Body "client_id=td-webapi-client&client_secret=td-webapi-secret-2024&grant_type=password&username=admin&password=admin123"

$token = $tokenResponse.access_token
$headers = @{ "Authorization" = "Bearer $token" }
```

#### Advanced Product Search
```powershell
$searchRequest = @{
    query = "gaming laptop"
    minPrice = 1000
    maxPrice = 3000
    minRating = 4.0
    page = 0
    size = 10
    sortBy = "rating"
    sortDirection = "desc"
} | ConvertTo-Json

$results = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/search/products/advanced" `
  -Method POST -Headers $headers -ContentType "application/json" -Body $searchRequest

Write-Host "Found $($results.totalElements) products"
$results.content | ForEach-Object { Write-Host "- $($_.name) ($($_.price))" }
```

#### Get Search Suggestions
```powershell
$suggestions = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/search/suggestions?q=sam&limit=5" `
  -Headers $headers

Write-Host "Product Suggestions:"
$suggestions.products | ForEach-Object { Write-Host "  - $($_.name)" }
Write-Host "Brand Suggestions:"
$suggestions.brands | ForEach-Object { Write-Host "  - $($_.name)" }
```

#### Sync Data to Elasticsearch
```powershell
# Sync all data
$syncResult = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/search/sync/all" `
  -Method POST -Headers $headers

Write-Host "Sync Status: $($syncResult.status)"
Write-Host "Message: $($syncResult.message)"
```

### cURL Examples

#### Quick Product Search
```bash
curl -X GET "http://localhost:8080/api/v1/search/products?q=laptop&minPrice=500&maxPrice=2000&page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

#### Brand Search
```bash
curl -X GET "http://localhost:8080/api/v1/search/brands?q=apple&isActive=true&page=0&size=5" \
  -H "Authorization: Bearer $TOKEN"
```

#### Global Search
```bash
curl -X GET "http://localhost:8080/api/v1/search/global?q=smartphone&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

## 🔄 Data Synchronization

### Automatic Sync (Recommended)
Khi sử dụng normal CRUD operations, data sẽ được tự động sync to Elasticsearch thông qua application events.

### Manual Sync Operations
```bash
# Sync all brands
POST /api/v1/admin/search/sync/brands

# Sync all products
POST /api/v1/admin/search/sync/products

# Sync all data
POST /api/v1/admin/search/sync/all

# Rebuild entire index
POST /api/v1/admin/search/rebuild

# Batch sync (for large datasets)
POST /api/v1/admin/search/sync/products/batch?batchSize=100
```

### Monitoring Sync Status
```powershell
# Check search health
$health = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/search/health" -Headers $headers
Write-Host "Search Status: $($health.status)"

# Check Elasticsearch directly
$esHealth = Invoke-RestMethod -Uri "http://localhost:9200/_cluster/health"
Write-Host "ES Cluster Status: $($esHealth.status)"
```

## 🎯 Performance Optimizations

### Index Settings
- **Single shard** cho development (scalable cho production)
- **No replicas** trong dev environment
- **Custom analyzers** cho Vietnamese text
- **Edge n-grams** cho fast autocomplete
- **Search-as-you-type** fields cho real-time suggestions

### Query Optimizations
- **Multi-match queries** với field boosting
- **Bool queries** với must/filter separation
- **Aggregations** cached for analytics
- **Pagination** optimized với scroll API support
- **Result scoring** tuned cho relevance

### Memory Management
- **Batch processing** cho large data sync
- **Connection pooling** với custom timeouts
- **JVM heap** configured cho Elasticsearch container
- **Query result** size limits để prevent memory issues

## 📈 Monitoring & Analytics

### Elasticsearch Metrics
```bash
# Cluster stats
GET http://localhost:9200/_cluster/stats

# Index stats
GET http://localhost:9200/products/_stats
GET http://localhost:9200/brands/_stats

# Node info
GET http://localhost:9200/_nodes/stats
```

### Application Metrics
- Search query execution times
- Index synchronization performance
- Memory usage during bulk operations
- Error rates và retry statistics

### Optional: Kibana Integration
Start Kibana để advanced analytics:
```yaml
# Add to docker-compose.yml
kibana:
  image: docker.elastic.co/kibana/kibana:8.11.3
  ports:
    - "5601:5601"
  environment:
    - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
```

## 🛠️ Troubleshooting

### Common Issues

#### Elasticsearch Not Starting
```powershell
# Check logs
docker-compose logs elasticsearch

# Check memory settings
docker stats td-elasticsearch

# Restart service
docker-compose restart elasticsearch
```

#### Index Mapping Conflicts
```bash
# Delete và recreate index
DELETE http://localhost:9200/products
DELETE http://localhost:9200/brands

# Restart application để auto-create với new mappings
```

#### Search Performance Issues
```bash
# Check slow queries
GET http://localhost:9200/_cat/indices?v&s=docs.count:desc

# Analyze query performance
GET http://localhost:9200/products/_search
{
  "profile": true,
  "query": { "match_all": {} }
}
```

#### Data Sync Issues
```powershell
# Check database connectivity
$dbTest = Invoke-RestMethod -Uri "http://localhost:8080/api/health"

# Force rebuild index
$rebuild = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/search/rebuild" `
  -Method POST -Headers $headers

# Check sync logs
docker-compose logs app | grep -i "elasticsearch\|sync"
```

### Health Checks
```powershell
# Complete health check script
function Test-ElasticsearchHealth {
    try {
        # Test ES cluster
        $esHealth = Invoke-RestMethod -Uri "http://localhost:9200/_cluster/health"
        Write-Host "✅ Elasticsearch: $($esHealth.status)" -ForegroundColor Green
        
        # Test app search endpoints
        $appHealth = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/search/health" -Headers $headers
        Write-Host "✅ App Search: $($appHealth.status)" -ForegroundColor Green
        
        # Test indices
        $indices = Invoke-RestMethod -Uri "http://localhost:9200/_cat/indices?format=json"
        $productIndex = $indices | Where-Object { $_.index -eq "products" }
        $brandIndex = $indices | Where-Object { $_.index -eq "brands" }
        
        if ($productIndex) {
            Write-Host "✅ Products index: $($productIndex.'docs.count') documents" -ForegroundColor Green
        }
        if ($brandIndex) {
            Write-Host "✅ Brands index: $($brandIndex.'docs.count') documents" -ForegroundColor Green
        }
        
    } catch {
        Write-Host "❌ Health check failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Test-ElasticsearchHealth
```

## 🔮 Future Enhancements

### Planned Features
- [ ] **Machine Learning** integration cho personalized search
- [ ] **Semantic search** với vector embeddings
- [ ] **Real-time analytics** dashboard
- [ ] **A/B testing** framework cho search algorithms
- [ ] **Multi-language** support expansion
- [ ] **Geo-spatial** search capabilities
- [ ] **Voice search** integration
- [ ] **Search result** personalization based on user behavior

### Performance Improvements
- [ ] **Multi-node** Elasticsearch cluster setup
- [ ] **Index templates** và lifecycle management
- [ ] **Query caching** strategies
- [ ] **Async indexing** với message queues
- [ ] **CDN integration** cho search result caching

---

**🎯 Happy Searching với TD WebAPI + Elasticsearch!**

*For more information, check the main project README và API documentation at `/swagger-ui.html`*