class PolicyModel {
  final String id;
  final String name;
  final String type;
  final String regoCode;
  final bool active;
  final DateTime lastUpdated;
  final String? description;
  final String? author;
  final int? version;

  const PolicyModel({
    required this.id,
    required this.name,
    required this.type,
    required this.regoCode,
    required this.active,
    required this.lastUpdated,
    this.description,
    this.author,
    this.version,
  });

  factory PolicyModel.fromJson(Map<String, dynamic> json) {
    return PolicyModel(
      id: json['id'] as String,
      name: json['name'] as String,
      type: json['type'] as String,
      regoCode: json['rego_code'] as String? ?? '',
      active: json['active'] as bool? ?? false,
      lastUpdated: json['last_updated'] != null
          ? DateTime.parse(json['last_updated'] as String)
          : DateTime.now(),
      description: json['description'] as String?,
      author: json['author'] as String?,
      version: json['version'] as int?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'type': type,
      'rego_code': regoCode,
      'active': active,
      'last_updated': lastUpdated.toIso8601String(),
      'description': description,
      'author': author,
      'version': version,
    };
  }

  PolicyModel copyWith({
    String? id,
    String? name,
    String? type,
    String? regoCode,
    bool? active,
    DateTime? lastUpdated,
    String? description,
    String? author,
    int? version,
  }) {
    return PolicyModel(
      id: id ?? this.id,
      name: name ?? this.name,
      type: type ?? this.type,
      regoCode: regoCode ?? this.regoCode,
      active: active ?? this.active,
      lastUpdated: lastUpdated ?? this.lastUpdated,
      description: description ?? this.description,
      author: author ?? this.author,
      version: version ?? this.version,
    );
  }
}

enum PolicyType {
  transactionLimit('transaction_limit'),
  rateLimit('rate_limit'),
  dataAccess('data_access'),
  compliance('compliance'),
  routing('routing'),
  custom('custom');

  final String value;
  const PolicyType(this.value);
}
