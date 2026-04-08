#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import dataclass

PRIMITIVE_TYPE_BY_DESCRIPTOR = {
    "V": "void",
    "Z": "boolean",
    "B": "byte",
    "S": "short",
    "C": "char",
    "I": "int",
    "J": "long",
    "F": "float",
    "D": "double",
}

PRIMITIVE_DESCRIPTOR_BY_TYPE = {value: key for key, value in PRIMITIVE_TYPE_BY_DESCRIPTOR.items()}


@dataclass(frozen=True, slots=True)
class ParsedMethodDescriptor:
    descriptor: str
    class_name: str
    method_name: str
    params: tuple[str, ...]
    return_type: str
    param_descriptors: tuple[str, ...]
    return_descriptor: str

    @property
    def class_descriptor(self) -> str:
        return self.descriptor.split("->", 1)[0]

    @property
    def short_descriptor(self) -> str:
        return f"({''.join(self.param_descriptors)}){self.return_descriptor}"

    @property
    def smali_signature(self) -> str:
        return f"{self.method_name}{self.short_descriptor}"


def type_name_to_descriptor(type_name: str) -> str:
    normalized = type_name.strip()
    if not normalized:
        raise ValueError("Type name must not be empty.")
    array_depth = 0
    while normalized.endswith("[]"):
        normalized = normalized[:-2].strip()
        array_depth += 1
    descriptor = PRIMITIVE_DESCRIPTOR_BY_TYPE.get(normalized)
    if descriptor is None:
        descriptor = f"L{normalized.replace('.', '/')};"
    return ("[" * array_depth) + descriptor


def class_name_to_descriptor(class_name: str) -> str:
    return type_name_to_descriptor(class_name)


def parse_type_descriptor(descriptor: str, start_index: int) -> tuple[str, str, int]:
    if start_index >= len(descriptor):
        raise ValueError("Type descriptor is truncated.")
    current = descriptor[start_index]
    primitive = PRIMITIVE_TYPE_BY_DESCRIPTOR.get(current)
    if primitive is not None:
        return primitive, current, start_index + 1
    if current == "L":
        end_index = descriptor.find(";", start_index)
        if end_index == -1:
            raise ValueError(f"Object type descriptor is missing `;`: {descriptor}")
        raw = descriptor[start_index + 1:end_index]
        return raw.replace("/", "."), descriptor[start_index:end_index + 1], end_index + 1
    if current == "[":
        nested_type, nested_descriptor, next_index = parse_type_descriptor(descriptor, start_index + 1)
        return f"{nested_type}[]", f"[{nested_descriptor}", next_index
    raise ValueError(f"Unsupported type descriptor segment: {descriptor[start_index:]}")


def descriptor_to_type_name(descriptor: str) -> str:
    type_name, _, next_index = parse_type_descriptor(descriptor, 0)
    if next_index != len(descriptor):
        raise ValueError(f"Unexpected trailing type descriptor content: {descriptor}")
    return type_name


def build_full_method_descriptor(
    *,
    class_name: str,
    method_name: str,
    params: list[str] | tuple[str, ...],
    return_type: str,
) -> str:
    param_descriptor = "".join(type_name_to_descriptor(item) for item in params)
    return f"{class_name_to_descriptor(class_name)}->{method_name}({param_descriptor}){type_name_to_descriptor(return_type)}"


def parse_method_descriptor(
    descriptor: str,
    *,
    class_name: str | None = None,
    method_name: str | None = None,
) -> ParsedMethodDescriptor:
    normalized = descriptor.strip()
    if not normalized:
        raise ValueError("Method descriptor must not be empty.")
    if normalized.startswith("("):
        if not class_name or not method_name:
            raise ValueError("Signature-only method descriptors require class_name and method_name.")
        normalized = f"{class_name_to_descriptor(class_name)}->{method_name}{normalized}"

    class_descriptor, separator, method_signature = normalized.partition("->")
    if not separator or not class_descriptor or not method_signature:
        raise ValueError(f"Invalid method descriptor: {descriptor}")
    signature_start = method_signature.find("(")
    signature_end = method_signature.find(")", signature_start + 1)
    if signature_start == -1 or signature_end == -1:
        raise ValueError(f"Invalid method descriptor: {descriptor}")

    parsed_class_name = descriptor_to_type_name(class_descriptor)
    parsed_method_name = method_signature[:signature_start]
    params_signature = method_signature[signature_start + 1:signature_end]
    return_signature = method_signature[signature_end + 1:]
    if not parsed_method_name or not return_signature:
        raise ValueError(f"Invalid method descriptor: {descriptor}")

    params: list[str] = []
    param_descriptors: list[str] = []
    index = 0
    while index < len(params_signature):
        param_type, param_descriptor, next_index = parse_type_descriptor(params_signature, index)
        params.append(param_type)
        param_descriptors.append(param_descriptor)
        index = next_index

    return_type, return_descriptor, next_index = parse_type_descriptor(return_signature, 0)
    if next_index != len(return_signature):
        raise ValueError(f"Unexpected trailing return descriptor content: {descriptor}")

    if class_name and parsed_class_name != class_name:
        raise ValueError(
            f"Method descriptor class `{parsed_class_name}` does not match provided class `{class_name}`."
        )
    if method_name and parsed_method_name != method_name:
        raise ValueError(
            f"Method descriptor name `{parsed_method_name}` does not match provided method `{method_name}`."
        )

    return ParsedMethodDescriptor(
        descriptor=normalized,
        class_name=parsed_class_name,
        method_name=parsed_method_name,
        params=tuple(params),
        return_type=return_type,
        param_descriptors=tuple(param_descriptors),
        return_descriptor=return_descriptor,
    )
